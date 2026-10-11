import { describe, it, expect, vi, beforeEach } from 'vitest'
import { brotliCompressSync } from 'zlib'
import { fakeRedisStore } from '../test/fakeRedis'

// Regression guard for the Render OOM (2026-07): populatePrices used to load the
// 24h-change reference with an unbounded `where: { timestamp: { lte: dayAgo } }`,
// which matches the entire retained history (~35 days, millions of rows) and,
// because `distinct` can't be pushed to Postgres under a timestamp `orderBy`,
// Prisma materialised every row in-process — enough to exhaust a 512MB instance.
// The query must stay bounded on BOTH ends.

const { skinFindMany, skinPriceFindMany, queryRaw, priceHistoryCreateMany, executeRawUnsafe } =
  vi.hoisted(() => ({
    skinFindMany: vi.fn(),
    skinPriceFindMany: vi.fn(),
    queryRaw: vi.fn(),
    priceHistoryCreateMany: vi.fn(),
    executeRawUnsafe: vi.fn(),
  }))

vi.mock('../db/prisma', () => ({
  prisma: {
    skin: { findMany: skinFindMany },
    skinPrice: { findMany: skinPriceFindMany },
    priceHistory: { createMany: priceHistoryCreateMany },
    $queryRaw: queryRaw,
    $executeRawUnsafe: executeRawUnsafe,
  },
}))

// `$queryRaw` is a tagged template: Prisma receives the literal fragments as the
// first argument and the interpolated values as the rest. The window bounds are
// therefore positional, not named — this pulls out the Date parameters in order.
function queryRawDates(call: unknown[]): Date[] {
  return call.slice(1).filter((v): v is Date => v instanceof Date)
}

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

// The daily catalog sync rides populatePrices; keep it offline and spyable.
const { syncSkinCatalogIfDue } = vi.hoisted(() => ({ syncSkinCatalogIfDue: vi.fn() }))
vi.mock('./populateSkins', () => ({ SOUVENIR_PREFIX: 'Souvenir ', syncSkinCatalogIfDue }))

const { populatePrices, getPriceHealth, PRICE_RUN_TIMESTAMP_KEY, MARKETPLACE_HEALTH_KEY } = await import('./populatePrices')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

const { CHANGE_REFERENCE_WINDOW_MS } = await import('../config/priceHistory')

describe('populatePrices — 24h-change reference query', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // One marketplace answering is enough for the run to reach the read query;
    // with all three down it now stops before touching the database.
    axiosGet.mockImplementation(async (url: string) =>
      url.includes('market.csgo.com')
        ? { data: [{ market_hash_name: 'AK-47 | Redline (Field-Tested)', price: '10', volume: '5' }] }
        : { data: [] },
    )
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 0 })
    executeRawUnsafe.mockResolvedValue(1)
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    redisGet.mockResolvedValue(null)
  })

  it('bounds the reference-price window on the lower end, not just the upper', async () => {
    await populatePrices(log)

    expect(queryRaw).toHaveBeenCalledTimes(1)

    // The lower bound is what prevents the whole retained table from loading.
    expect(queryRawDates(queryRaw.mock.calls[0])).toHaveLength(2)
  })

  it('makes the window exactly CHANGE_REFERENCE_WINDOW_MS wide, ending ~24h ago', async () => {
    const before = Date.now()
    await populatePrices(log)
    const after = Date.now()

    // Interpolation order in the query is `>= windowStart AND <= dayAgo`.
    const [gte, lte] = queryRawDates(queryRaw.mock.calls[0])

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

  // Prisma's `distinct` cannot be pushed down to Postgres under a timestamp
  // `orderBy`, so it fetches the whole window and dedupes in-process — ~4x the
  // rows at the 6h cadence, every run. Dedup has to happen in the database.
  it('dedupes per skin in the database rather than in the client', async () => {
    await populatePrices(log)

    const sql = queryRaw.mock.calls[0][0].join('?')
    expect(sql).toContain('DISTINCT ON ("skinId")')
    expect(sql).toContain('ORDER BY "skinId", "timestamp" DESC')
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
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 0 })
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    executeRawUnsafe.mockResolvedValue(1)
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
    queryRaw.mockResolvedValue([])
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
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 1 })
    executeRawUnsafe.mockResolvedValue(1)
  })

  it('leaves no cached top-movers list behind after a run', async () => {
    fake.store.set('top-movers:rising:20', [{ skinId: 'stale' }])
    fake.store.set('top-movers:falling:20', [{ skinId: 'stale' }])

    await populatePrices(log)

    expect([...fake.store.keys()].filter((key) => key.startsWith('top-movers:'))).toEqual([])
  })
})

// The write path used to be one `prisma.skinPrice.upsert()` per skin — ~24k
// sequential round trips per run, four runs a day. It was never slow enough to
// notice and never wrong, it just moved enough protocol overhead to put the job
// on course to exhaust Neon's monthly network transfer allowance by itself.
// Nothing about the *result* changes when it regresses, so the round-trip count
// is the only thing that can pin it.
describe('populatePrices — bulk write path', () => {
  const SKIN_COUNT = 1200

  function catalog(n: number) {
    return Array.from({ length: n }, (_, i) => ({
      id: `skin-${i}`,
      marketHashName: `Weapon | Skin ${i} (Field-Tested)`,
    }))
  }

  beforeEach(() => {
    vi.clearAllMocks()
    skinFindMany.mockResolvedValue(catalog(SKIN_COUNT))
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: SKIN_COUNT })
    executeRawUnsafe.mockResolvedValue(1)
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    redisGet.mockResolvedValue(null)

    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('market.csgo.com')) {
        return {
          data: catalog(SKIN_COUNT).map((s, i) => ({
            market_hash_name: s.marketHashName,
            price: String(10 + i),
            volume: '5',
          })),
        }
      }
      return { data: [] }
    })
  })

  it('batches the writes instead of issuing one statement per skin', async () => {
    await populatePrices(log)

    // 1200 rows at a 500-row batch = 3 statements. The point of the assertion is
    // the upper bound: anything near SKIN_COUNT means the per-row write is back.
    expect(executeRawUnsafe).toHaveBeenCalledTimes(3)
  })

  it('writes every priced skin exactly once across the batches', async () => {
    await populatePrices(log)

    const writtenSkinIds = executeRawUnsafe.mock.calls.flatMap((call) =>
      call.slice(1).filter((v: unknown) => typeof v === 'string' && v.startsWith('skin-')),
    )

    expect(writtenSkinIds).toHaveLength(SKIN_COUNT)
    expect(new Set(writtenSkinIds).size).toBe(SKIN_COUNT)
  })

  it('upserts on the skinId unique constraint rather than inserting duplicates', async () => {
    await populatePrices(log)

    const sql = executeRawUnsafe.mock.calls[0][0] as string
    expect(sql).toContain('ON CONFLICT ("skinId") DO UPDATE')
  })

  // CS:GO Market's `volume` (its count of listings) is the liquidity signal top
  // movers needs; the column existed but nothing ever filled it.
  it("writes CS:GO Market's volume into volume24h", async () => {
    await populatePrices(log)

    const [sql, ...params] = executeRawUnsafe.mock.calls[0]
    expect(sql).toMatch(/"volume24h"\s+= EXCLUDED\."volume24h"/)
    // 9 columns per row; volume is the last one of the first row.
    expect(params[8]).toBe(5)
  })

  it('still reports the rows it wrote in the run summary', async () => {
    const summary = await populatePrices(log)

    expect(summary).toEqual({ updated: SKIN_COUNT, historyRows: SKIN_COUNT })
  })
})

describe('populatePrices — catalog and souvenirs', () => {
  const SOUVENIR = 'Souvenir AWP | Dragon Lore (Factory New)'

  beforeEach(() => {
    vi.clearAllMocks()
    skinFindMany.mockResolvedValue([
      { id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' },
      { id: 'souvenir-1', marketHashName: SOUVENIR },
    ])
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 1 })
    executeRawUnsafe.mockResolvedValue(1)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    redisGet.mockResolvedValue(null)
    syncSkinCatalogIfDue.mockResolvedValue(null)
    axiosGet.mockImplementation(async (url: string) =>
      url.includes('market.csgo.com')
        ? {
            data: [
              { market_hash_name: 'AK-47 | Redline (Field-Tested)', price: '10', volume: '5' },
              { market_hash_name: SOUVENIR, price: '90000', volume: '2' },
            ],
          }
        : { data: [] },
    )
  })

  // Run before the catalog is read, so a skin added today is priced today.
  it('syncs the catalog before reading it, telling it what the marketplaces list', async () => {
    await populatePrices(log)

    expect(syncSkinCatalogIfDue.mock.invocationCallOrder[0]).toBeLessThan(skinFindMany.mock.invocationCallOrder[0])
    const isListed = syncSkinCatalogIfDue.mock.calls[0][1] as (name: string) => boolean
    expect(isListed(SOUVENIR)).toBe(true)
    expect(isListed('Souvenir P250 | Sand Dune (Factory New)')).toBe(false)
  })

  it('still prices the catalog when the catalog sync fails', async () => {
    syncSkinCatalogIfDue.mockRejectedValue(new Error('GitHub down'))

    expect((await populatePrices(log)).updated).toBe(2)
  })

  // Charting souvenirs would cost ~65 MB of the 512 MB cap.
  it('prices souvenirs but writes no history for them', async () => {
    const summary = await populatePrices(log)

    expect(summary).toEqual({ updated: 2, historyRows: 1 })
    expect(priceHistoryCreateMany).toHaveBeenCalledWith({
      data: [expect.objectContaining({ skinId: 'skin-1' })],
    })
  })
})

// A price is only as good as the number of independent parties quoting it. The
// pipeline used to take a plain MIN across the three marketplaces, which means
// trusting whichever one was most wrong: a single seller parking a $2 skin at
// $61 938 set the price, the 24h change, and the top of the home screen.
describe('populatePrices — outlier rejection across marketplaces', () => {
  const NAME = 'AK-47 | Redline (Field-Tested)'

  /** Wire the three bulk endpoints to quote one skin at the given prices. */
  function quoteOneSkin(quotes: {
    skinport?: number
    csgoMarket?: number
    waxpeer?: number
  }) {
    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('skinport')) {
        const items =
          quotes.skinport === undefined
            ? []
            : [{ market_hash_name: NAME, min_price: quotes.skinport, suggested_price: null }]
        // Skinport is fetched as an undecompressed Brotli arraybuffer.
        return { data: brotliCompressSync(Buffer.from(JSON.stringify(items), 'utf8')) }
      }
      if (url.includes('market.csgo.com')) {
        return {
          data:
            quotes.csgoMarket === undefined
              ? []
              : [{ market_hash_name: NAME, price: String(quotes.csgoMarket), volume: '1' }],
        }
      }
      if (url.includes('waxpeer')) {
        return {
          data: {
            success: true,
            // Waxpeer reports USD x 1000.
            items:
              quotes.waxpeer === undefined ? [] : [{ name: NAME, min: quotes.waxpeer * 1000 }],
          },
        }
      }
      return { data: [] }
    })
  }

  /** The `lowestPrice` bind parameter of the single written row. */
  function writtenLowestPrice(): number {
    expect(executeRawUnsafe).toHaveBeenCalledTimes(1)
    // Column order: id, skinId, skinport, csgo, waxpeer, lowestPrice, change, updatedAt.
    return executeRawUnsafe.mock.calls[0][6] as number
  }

  beforeEach(() => {
    vi.clearAllMocks()
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: NAME }])
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 1 })
    executeRawUnsafe.mockResolvedValue(1)
    redisDel.mockResolvedValue(undefined)
    redisKeys.mockResolvedValue([])
    redisSet.mockResolvedValue('OK')
    redisGet.mockResolvedValue(null)
  })

  it('asks Skinport for tradable listings, not trade-locked ones', async () => {
    quoteOneSkin({ skinport: 10 })
    await populatePrices(log)

    const skinportCall = axiosGet.mock.calls.find((c) => String(c[0]).includes('skinport'))
    expect(skinportCall?.[1]?.params?.tradable).toBe(1)
  })

  it('drops a quote far above the median of three', async () => {
    // Two marketplaces agree around $100; the third is 19x the median.
    quoteOneSkin({ skinport: 100, csgoMarket: 105, waxpeer: 2000 })
    await populatePrices(log)

    expect(writtenLowestPrice()).toBe(100)
  })

  it('drops a quote far below the median of three', async () => {
    // The dangerous direction: a plain MIN would publish $0.03 as the price of a
    // $100 skin, and the next run would report it as a +300 000% riser.
    quoteOneSkin({ skinport: 0.03, csgoMarket: 100, waxpeer: 105 })
    await populatePrices(log)

    expect(writtenLowestPrice()).toBe(100)
  })

  it('keeps a spread that is merely wide, not absurd', async () => {
    // 2x off the median is ordinary wear/pattern dispersion, not a lie.
    quoteOneSkin({ skinport: 50, csgoMarket: 100, waxpeer: 105 })
    await populatePrices(log)

    expect(writtenLowestPrice()).toBe(50)
  })

  it('takes the plain minimum when only two marketplaces quote', async () => {
    // No majority exists, so nothing identifies which of the pair is lying.
    // Rejecting one arbitrarily would be guessing; top movers refuses the row
    // instead, and the detail screen shows it with both prices visible.
    quoteOneSkin({ skinport: 10, waxpeer: 2000 })
    await populatePrices(log)

    expect(writtenLowestPrice()).toBe(10)
  })

  it('still writes the individual marketplace prices it was quoted', async () => {
    // Rejection changes which price is called "lowest"; it must not silently
    // erase what each marketplace actually said, which the detail screen shows.
    quoteOneSkin({ skinport: 100, csgoMarket: 105, waxpeer: 2000 })
    await populatePrices(log)

    const [, , , skinport, csgo, waxpeer] = executeRawUnsafe.mock.calls[0]
    expect({ skinport, csgo, waxpeer }).toEqual({ skinport: 100, csgo: 105, waxpeer: 2000 })
  })
})

describe('populatePrices — one marketplace down', () => {
  const NAME = 'AK-47 | Redline (Field-Tested)'
  const HOUR = 60 * 60 * 1000

  /** Skinport and Waxpeer answer normally; CS:GO Market returns nothing. */
  function csgoMarketDown() {
    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('skinport')) {
        const items = [{ market_hash_name: NAME, min_price: 30, suggested_price: null }]
        return { data: brotliCompressSync(Buffer.from(JSON.stringify(items), 'utf8')) }
      }
      if (url.includes('waxpeer')) return { data: { success: true, items: [{ name: NAME, min: 31_000 }] } }
      return { data: [] }
    })
  }

  /** The bind parameters of the single written row, by column. */
  function writtenRow() {
    const p = executeRawUnsafe.mock.calls[0]
    return { skinport: p[3], csgo: p[4], waxpeer: p[5], lowest: p[6], volume: p[9] }
  }

  const redis = fakeRedisStore()

  beforeEach(() => {
    vi.clearAllMocks()
    redis.store.clear()
    redisGet.mockImplementation(redis.get)
    redisSet.mockImplementation(redis.set)
    redisKeys.mockImplementation(redis.keys)
    redisDel.mockImplementation(redis.del)
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: NAME }])
    skinPriceFindMany.mockResolvedValue([
      { skinId: 'skin-1', skinportPrice: 29, csgoMarketPrice: 12, waxpeerPrice: 30, volume24h: 7 },
    ])
    queryRaw.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 1 })
    executeRawUnsafe.mockResolvedValue(1)
  })

  // Writing NULL for a marketplace that merely failed to answer moved
  // lowestPrice to whichever survived: an artificial jump, a false SELL_ABOVE
  // alert, and a polluted history point — then the reverse on the next run.
  it('keeps its last prices instead of blanking them', async () => {
    redis.store.set(MARKETPLACE_HEALTH_KEY, { csgoMarket: { count: 1, at: Date.now() - 6 * HOUR } })
    csgoMarketDown()

    await populatePrices(log)

    expect(writtenRow()).toMatchObject({ skinport: 30, csgo: 12, waxpeer: 31, lowest: 12, volume: 7 })
    expect(log.warn).toHaveBeenCalled()
  })

  it('stops carrying them once its last good run is over a day old', async () => {
    redis.store.set(MARKETPLACE_HEALTH_KEY, { csgoMarket: { count: 1, at: Date.now() - 25 * HOUR } })
    csgoMarketDown()

    await populatePrices(log)

    expect(writtenRow()).toMatchObject({ csgo: null, lowest: 30 })
  })

  it('treats a sharp drop in item count as down, not just an empty answer', async () => {
    redis.store.set(MARKETPLACE_HEALTH_KEY, { csgoMarket: { count: 20_000, at: Date.now() - 6 * HOUR } })
    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('market.csgo.com')) return { data: [{ market_hash_name: NAME, price: '999', volume: '1' }] }
      if (url.includes('waxpeer')) return { data: { success: true, items: [{ name: NAME, min: 31_000 }] } }
      return { data: [] }
    })

    await populatePrices(log)

    expect(writtenRow().csgo).toBe(12)
  })

  it('reads previous prices only when it has to', async () => {
    redis.store.set(MARKETPLACE_HEALTH_KEY, { csgoMarket: { count: 1, at: Date.now() - HOUR } })
    axiosGet.mockImplementation(async (url: string) => {
      if (url.includes('market.csgo.com')) return { data: [{ market_hash_name: NAME, price: '11', volume: '3' }] }
      return { data: [] }
    })

    await populatePrices(log)

    expect(skinPriceFindMany).not.toHaveBeenCalled()
  })

  // Carrying every marketplace forward would keep /health "fresh" over prices
  // nothing refreshed — the failure the freshness marker exists to catch.
  it('writes nothing when every marketplace is down, even with prices to carry', async () => {
    redis.store.set(MARKETPLACE_HEALTH_KEY, {
      skinport: { count: 1, at: Date.now() - HOUR },
      csgoMarket: { count: 1, at: Date.now() - HOUR },
      waxpeer: { count: 1, at: Date.now() - HOUR },
    })
    axiosGet.mockResolvedValue({ data: [] })

    const summary = await populatePrices(log)

    expect(summary.updated).toBe(0)
    expect(executeRawUnsafe).not.toHaveBeenCalled()
  })

  it("remembers each healthy marketplace's size for the next run", async () => {
    csgoMarketDown()

    await populatePrices(log)

    const health = redis.store.get(MARKETPLACE_HEALTH_KEY) as Record<string, { count: number }>
    expect(health.skinport.count).toBe(1)
    expect(health.waxpeer.count).toBe(1)
    expect(health.csgoMarket).toBeUndefined()
  })

  // The alert check runs right after this job and reads prices through a 5-min
  // cache; without clearing it, alerts could fire on the previous run's prices.
  it('clears the per-skin price cache once new prices are written', async () => {
    redis.store.set('prices:skin-1', { lowestPrice: 1 })
    csgoMarketDown()

    await populatePrices(log)

    expect(redis.store.has('prices:skin-1')).toBe(false)
  })
})
