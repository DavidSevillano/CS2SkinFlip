import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fakeRedisStore } from '../test/fakeRedis'

// Regression guard for the Render OOM (2026-07): populatePrices used to load the
// 24h-change reference with an unbounded `where: { timestamp: { lte: dayAgo } }`,
// which matches the entire retained history (~35 days, millions of rows) and,
// because `distinct` can't be pushed to Postgres under a timestamp `orderBy`,
// Prisma materialised every row in-process — enough to exhaust a 512MB instance.
// The query must stay bounded on BOTH ends.

const { skinFindMany, priceHistoryFindMany, priceHistoryCreateMany, skinPriceUpsert } =
  vi.hoisted(() => ({
    skinFindMany: vi.fn(),
    priceHistoryFindMany: vi.fn(),
    priceHistoryCreateMany: vi.fn(),
    skinPriceUpsert: vi.fn(),
  }))

vi.mock('../db/prisma', () => ({
  prisma: {
    skin: { findMany: skinFindMany },
    priceHistory: { findMany: priceHistoryFindMany, createMany: priceHistoryCreateMany },
    skinPrice: { upsert: skinPriceUpsert },
  },
}))

const { redisDel, redisSet, redisGet, redisKeys } = vi.hoisted(() => ({
  redisDel: vi.fn(),
  redisSet: vi.fn(),
  redisGet: vi.fn(),
  redisKeys: vi.fn(),
}))
vi.mock('../redis/client', () => ({
  redis: { del: redisDel, set: redisSet, get: redisGet, keys: redisKeys },
  // populatePrices pulls in services/prices for the cache invalidator, which
  // imports CACHE_TTL at module scope — a missing named export fails the link.
  CACHE_TTL: { TOP_MOVERS: 900, SKIN_PRICES: 300 },
}))

// Keep the marketplace fetchers offline: an empty payload yields empty price
// maps, so no upserts/history are written and the test isolates the read query.
const { axiosGet } = vi.hoisted(() => ({ axiosGet: vi.fn() }))
vi.mock('axios', () => ({ default: { get: axiosGet } }))

const { populatePrices, getPriceHealth, PRICE_RUN_TIMESTAMP_KEY } = await import('./populatePrices')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

const { CHANGE_REFERENCE_WINDOW_MS } = await import('../config/priceHistory')

describe('populatePrices — 24h-change reference query', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axiosGet.mockResolvedValue({ data: [] })
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])
    priceHistoryFindMany.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 0 })
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    redisGet.mockResolvedValue(null)
  })

  it('bounds the reference-price window on the lower end (has a gte), not just lte', async () => {
    await populatePrices(log)

    expect(priceHistoryFindMany).toHaveBeenCalledTimes(1)
    const where = priceHistoryFindMany.mock.calls[0][0].where

    // The lower bound is what prevents the whole retained table from loading.
    expect(where.timestamp.gte).toBeInstanceOf(Date)
    expect(where.timestamp.lte).toBeInstanceOf(Date)
  })

  it('makes the window exactly CHANGE_REFERENCE_WINDOW_MS wide, ending ~24h ago', async () => {
    const before = Date.now()
    await populatePrices(log)
    const after = Date.now()

    const { gte, lte } = priceHistoryFindMany.mock.calls[0][0].where.timestamp

    // Both bounds derive from a Date.now() taken *inside* the call, which sits
    // somewhere in [before, after]. So `after` is the only safe reference for a
    // lower-bound assertion — comparing against `before` makes these off-by-a-
    // millisecond flaky whenever the clock ticks mid-call.
    expect(after - lte.getTime()).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000)
    expect(before - lte.getTime()).toBeLessThanOrEqual(24 * 60 * 60 * 1000)

    // lte is ~24h ago; gte is one window below that.
    expect(lte.getTime() - gte.getTime()).toBe(CHANGE_REFERENCE_WINDOW_MS)
    expect(after - gte.getTime()).toBeGreaterThanOrEqual(
      24 * 60 * 60 * 1000 + CHANGE_REFERENCE_WINDOW_MS,
    )
  })
})

// The freshness marker is what turns the uptime pinger into a pipeline monitor,
// so the case it must never get wrong is the silent one: every marketplace fetch
// failing still runs the job to completion (each fetcher catches its own error
// and returns an empty map), and marking that as a successful run would leave
// /health green while prices froze.
describe('populatePrices — price freshness marker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axiosGet.mockResolvedValue({ data: [] })
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])
    priceHistoryFindMany.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 0 })
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    skinPriceUpsert.mockResolvedValue({})
  })

  it('does not mark the run successful when no marketplace returned a price', async () => {
    await populatePrices(log)

    const marked = redisSet.mock.calls.some(([key]) => key === PRICE_RUN_TIMESTAMP_KEY)
    expect(marked).toBe(false)
    expect(log.error).toHaveBeenCalled()
  })

  it('marks the run successful once at least one skin got a price', async () => {
    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('market.csgo.com')) {
        return { data: [{ market_hash_name: 'AK-47 | Redline (Field-Tested)', price: '12.50', volume: '5' }] }
      }
      return { data: [] }
    })

    const before = Date.now()
    await populatePrices(log)
    const after = Date.now()

    const call = redisSet.mock.calls.find(([key]) => key === PRICE_RUN_TIMESTAMP_KEY)
    expect(call).toBeDefined()
    expect(call![1]).toBeGreaterThanOrEqual(before)
    expect(call![1]).toBeLessThanOrEqual(after)
  })

  it('stays outside the prices:* namespace that app.ts wipes on every boot', () => {
    expect(PRICE_RUN_TIMESTAMP_KEY.startsWith('prices:')).toBe(false)
  })
})

describe('getPriceHealth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reports fresh inside the 8h window', async () => {
    redisGet.mockResolvedValue(Date.now() - 7 * 60 * 60 * 1000)
    expect(await getPriceHealth()).toMatchObject({ freshness: 'fresh' })
  })

  it('reports stale past the 8h window', async () => {
    redisGet.mockResolvedValue(Date.now() - 9 * 60 * 60 * 1000)
    expect(await getPriceHealth()).toMatchObject({ freshness: 'stale' })
  })

  it('reports unknown when the marker was never written', async () => {
    redisGet.mockResolvedValue(null)
    expect(await getPriceHealth()).toEqual({ freshness: 'unknown', lastRun: null })
  })

  it('reports unknown instead of throwing when Redis is down', async () => {
    redisGet.mockRejectedValue(new Error('upstash unreachable'))
    expect(await getPriceHealth()).toEqual({ freshness: 'unknown', lastRun: null })
  })

  it('accepts a marker that comes back as a string', async () => {
    redisGet.mockResolvedValue(String(Date.now() - 60 * 1000))
    expect(await getPriceHealth()).toMatchObject({ freshness: 'fresh' })
  })
})

describe('populatePrices — run summary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axiosGet.mockResolvedValue({ data: [] })
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])
    priceHistoryFindMany.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 0 })
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    redisGet.mockResolvedValue(null)
  })

  it('reports zero updates when every marketplace returns nothing', async () => {
    const summary = await populatePrices(log)

    expect(summary).toEqual({ updated: 0, historyRows: 0 })
  })
})

// The run rewrites every price the top-movers lists are built from, so leaving
// them cached serves numbers the run just superseded. The bug this pins was
// silent for exactly that reason: nothing failed, the lists were merely up to
// 15 minutes (the TTL) behind the prices shown everywhere else in the app.
describe('populatePrices — top-movers cache', () => {
  let fake: ReturnType<typeof fakeRedisStore>

  beforeEach(() => {
    vi.clearAllMocks()
    fake = fakeRedisStore()
    redisGet.mockImplementation(fake.get)
    redisSet.mockImplementation(fake.set)
    redisKeys.mockImplementation(fake.keys)
    redisDel.mockImplementation(fake.del)

    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('market.csgo.com')) {
        return { data: [{ market_hash_name: 'AK-47 | Redline (Field-Tested)', price: '12.50', volume: '5' }] }
      }
      return { data: [] }
    })
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])
    priceHistoryFindMany.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 1 })
    skinPriceUpsert.mockResolvedValue({})
  })

  it('leaves no cached top-movers list behind after a run', async () => {
    fake.store.set('top-movers:rising:20', [{ skinId: 'stale' }])
    fake.store.set('top-movers:falling:20', [{ skinId: 'stale' }])

    await populatePrices(log)

    expect([...fake.store.keys()].filter((key) => key.startsWith('top-movers:'))).toEqual([])
  })
})
