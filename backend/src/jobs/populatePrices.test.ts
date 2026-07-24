import { describe, it, expect, vi, beforeEach } from 'vitest'
import { brotliCompressSync } from 'zlib'
import { fakeRedisStore } from '../test/fakeRedis'

// Regression guard for the Render OOM (2026-07): populatePrices used to load the
// 24h-change reference with an unbounded `where: { timestamp: { lte: dayAgo } }`,
// which matches the entire retained history (~35 days, millions of rows) and,
// because `distinct` can't be pushed to Postgres under a timestamp `orderBy`,
// Prisma materialised every row in-process — enough to exhaust a 512MB instance.
// The query must stay bounded on BOTH ends.

const { skinFindMany, queryRaw, priceHistoryCreateMany, executeRawUnsafe } =
  vi.hoisted(() => ({
    skinFindMany: vi.fn(),
    queryRaw: vi.fn(),
    priceHistoryCreateMany: vi.fn(),
    executeRawUnsafe: vi.fn(),
  }))

vi.mock('../db/prisma', () => ({
  prisma: {
    skin: { findMany: skinFindMany },
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

const { populatePrices, getPriceHealth, PRICE_RUN_TIMESTAMP_KEY } = await import('./populatePrices')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

const { CHANGE_REFERENCE_WINDOW_MS } = await import('../config/priceHistory')

describe('populatePrices — 24h-change reference query', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axiosGet.mockResolvedValue({ data: [] })
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

  // The job never computes volume24h. Naming it in the statement would blank it
  // on every run for whatever else does.
  it('leaves volume24h untouched', async () => {
    await populatePrices(log)

    const sql = executeRawUnsafe.mock.calls[0][0] as string
    expect(sql).not.toContain('volume24h')
  })

  it('still reports the rows it wrote in the run summary', async () => {
    const summary = await populatePrices(log)

    expect(summary).toEqual({ updated: SKIN_COUNT, historyRows: SKIN_COUNT })
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
