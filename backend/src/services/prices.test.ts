import { describe, it, expect, vi, beforeEach } from 'vitest'
import { readdirSync, readFileSync } from 'fs'
import { join, relative } from 'path'
import { fakeRedisStore } from '../test/fakeRedis'

// Same class of query as populatePrices: getTopMovers reads the 24h-change
// reference from PriceHistory with `distinct`. It's bounded to <=100 skinIds so
// it never OOMed, but the window must stay bounded on the lower end too so the
// query can't degrade into scanning the whole retained table.

const { skinFindMany, priceHistoryFindMany } = vi.hoisted(() => ({
  skinFindMany: vi.fn(),
  priceHistoryFindMany: vi.fn(),
}))

vi.mock('../db/prisma', () => ({
  prisma: {
    skin: { findMany: skinFindMany },
    priceHistory: { findMany: priceHistoryFindMany },
  },
}))

const { redisGet, redisSet, redisDel, redisKeys } = vi.hoisted(() => ({
  redisGet: vi.fn(),
  redisSet: vi.fn(),
  redisDel: vi.fn(),
  redisKeys: vi.fn(),
}))
vi.mock('../redis/client', () => ({
  redis: { get: redisGet, set: redisSet, del: redisDel, keys: redisKeys },
  CACHE_TTL: { TOP_MOVERS: 900, SKIN_PRICES: 300 },
}))

const { PriceService, invalidateTopMoversCache } = await import('./prices')

const { CHANGE_REFERENCE_WINDOW_MS } = await import('../config/priceHistory')

const { MIN_TOP_MOVER_PRICE } = await import('../config/priceQuality')

describe('PriceService.getTopMovers — 24h-change reference query', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    redisGet.mockResolvedValue(null)
    skinFindMany.mockResolvedValue([
      {
        id: 'skin-1',
        marketHashName: 'AK-47 | Redline (Field-Tested)',
        name: 'AK-47 | Redline',
        iconUrl: 'http://img/1.png',
        // Two agreeing quotes: the minimum a skin needs to be eligible at all.
        price: { lowestPrice: 10, skinportPrice: 10, csgoMarketPrice: 11, waxpeerPrice: null, updatedAt: new Date() },
      },
    ])
    priceHistoryFindMany.mockResolvedValue([])
  })

  it('bounds the reference-price window on the lower end (has a gte), not just lte', async () => {
    const before = Date.now()
    await new PriceService().getTopMovers('rising', 20)
    const after = Date.now()

    expect(priceHistoryFindMany).toHaveBeenCalledTimes(1)
    const { gte, lte } = priceHistoryFindMany.mock.calls[0][0].where.timestamp

    expect(gte).toBeInstanceOf(Date)
    expect(lte).toBeInstanceOf(Date)
    // `after` is the only safe reference for a lower-bound assertion: both
    // bounds derive from a Date.now() taken inside the call, so comparing
    // against `before` is flaky by a millisecond whenever the clock ticks.
    expect(after - lte.getTime()).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000)
    expect(before - lte.getTime()).toBeLessThanOrEqual(24 * 60 * 60 * 1000)
    expect(lte.getTime() - gte.getTime()).toBe(CHANGE_REFERENCE_WINDOW_MS)
    expect(after - gte.getTime()).toBeGreaterThanOrEqual(
      24 * 60 * 60 * 1000 + CHANGE_REFERENCE_WINDOW_MS,
    )
  })
})

// The home screen's Rising tab was led by a Battle-Scarred M4A4 | Bullet Rain at
// $61 938, up 50 608%, on the word of one marketplace. Measured 2026-07-24:
// skins with a single quote are 3.7% of the catalog and supplied 15 of the top
// 20 risers. A 24h change is only as trustworthy as the price underneath it.
describe('PriceService.getTopMovers — corroboration', () => {
  const priced = (over: Record<string, unknown> = {}) => ({
    lowestPrice: 10,
    skinportPrice: 10,
    csgoMarketPrice: 11,
    waxpeerPrice: null,
    updatedAt: new Date(),
    ...over,
  })

  const skin = (id: string, price: Record<string, unknown>) => ({
    id,
    marketHashName: `Skin ${id} (Field-Tested)`,
    name: `Skin ${id}`,
    iconUrl: `http://img/${id}.png`,
    price,
  })

  beforeEach(() => {
    vi.clearAllMocks()
    redisGet.mockResolvedValue(null)
    redisSet.mockResolvedValue('OK')
  })

  it('asks the database for skins quoted by at least two marketplaces', async () => {
    skinFindMany.mockResolvedValue([])
    priceHistoryFindMany.mockResolvedValue([])

    await new PriceService().getTopMovers('rising', 20)

    // Pushed down to SQL rather than filtered in-process: it disqualifies most
    // of what used to sort to the top, and rejected rows still cost egress on a
    // metered connection if they come back first.
    const { OR } = skinFindMany.mock.calls[0][0].where.price
    expect(OR).toEqual([
      { skinportPrice: { not: null }, csgoMarketPrice: { not: null } },
      { skinportPrice: { not: null }, waxpeerPrice: { not: null } },
      { csgoMarketPrice: { not: null }, waxpeerPrice: { not: null } },
    ])
  })

  it('drops a skin whose two quotes disagree wildly', async () => {
    // Real shape from production: ★ StatTrak™ Talon Knife | Stained, quoted at
    // $373 and $2 762, sitting in the Falling tab at -86.5%.
    skinFindMany.mockResolvedValue([
      skin('liar', priced({ lowestPrice: 373, skinportPrice: 373, csgoMarketPrice: 2762 })),
      skin('honest', priced({ lowestPrice: 100, skinportPrice: 100, csgoMarketPrice: 104 })),
    ])
    priceHistoryFindMany.mockResolvedValue([
      { skinId: 'liar', price: 2762 },
      { skinId: 'honest', price: 80 },
    ])

    const movers = await new PriceService().getTopMovers('rising', 20)

    expect(movers.map((m) => m.skinId)).toEqual(['honest'])
  })

  it('keeps a skin whose quotes merely differ by a normal spread', async () => {
    // p90 of real cross-marketplace disagreement is 1.5x — that is a market,
    // not an error, and filtering it out would empty the list.
    skinFindMany.mockResolvedValue([
      skin('wide', priced({ lowestPrice: 100, skinportPrice: 100, csgoMarketPrice: 150 })),
    ])
    priceHistoryFindMany.mockResolvedValue([{ skinId: 'wide', price: 80 }])

    const movers = await new PriceService().getTopMovers('rising', 20)

    expect(movers.map((m) => m.skinId)).toEqual(['wide'])
  })

  it('drops a row that reaches it with only one usable quote', async () => {
    // Belt and braces: the SQL filter is the gate, but a null price column can
    // still arrive (a marketplace dropping to null between write and read), and
    // an unverifiable price must not rank on the strength of a technicality.
    skinFindMany.mockResolvedValue([
      skin('alone', priced({ lowestPrice: 500, skinportPrice: 500, csgoMarketPrice: null })),
    ])
    priceHistoryFindMany.mockResolvedValue([{ skinId: 'alone', price: 1 }])

    const movers = await new PriceService().getTopMovers('rising', 20)

    expect(movers).toEqual([])
  })
})

// Percentage alone rewards cheap skins for nothing: a $1.20 item moving $0.57
// is +90% and outranks a $336 knife moving $216. Both numbers are correct; only
// one of them is a trade anybody can make.
describe('PriceService.getTopMovers — movement worth acting on', () => {
  const skin = (id: string, lowestPrice: number, second: number) => ({
    id,
    marketHashName: `Skin ${id} (Field-Tested)`,
    name: `Skin ${id}`,
    iconUrl: `http://img/${id}.png`,
    price: {
      lowestPrice,
      skinportPrice: lowestPrice,
      csgoMarketPrice: second,
      waxpeerPrice: null,
      updatedAt: new Date(),
    },
  })

  beforeEach(() => {
    vi.clearAllMocks()
    redisGet.mockResolvedValue(null)
    redisSet.mockResolvedValue('OK')
  })

  it('makes the database apply the price floor', async () => {
    skinFindMany.mockResolvedValue([])
    priceHistoryFindMany.mockResolvedValue([])

    await new PriceService().getTopMovers('rising', 20)

    expect(skinFindMany.mock.calls[0][0].where.price.lowestPrice).toEqual({
      gte: MIN_TOP_MOVER_PRICE,
    })
  })

  it('drops a dramatic percentage that is worth pennies', async () => {
    // +90% on a $1.20 skin. It led the list on percentage alone; the whole move
    // is $0.57, which no marketplace makes tradeable after fees.
    skinFindMany.mockResolvedValue([
      skin('pennies', 1.2, 1.22),
      skin('real', 336, 340),
    ])
    priceHistoryFindMany.mockResolvedValue([
      { skinId: 'pennies', price: 0.63 },
      { skinId: 'real', price: 119 },
    ])

    const movers = await new PriceService().getTopMovers('rising', 20)

    expect(movers.map((m) => m.skinId)).toEqual(['real'])
  })

  it('keeps a modest percentage that moved real money', async () => {
    // +12% is unremarkable next to +90%, but it is $60 rather than $0.57.
    skinFindMany.mockResolvedValue([skin('chunky', 560, 575)])
    priceHistoryFindMany.mockResolvedValue([{ skinId: 'chunky', price: 500 }])

    const movers = await new PriceService().getTopMovers('rising', 20)

    expect(movers.map((m) => m.skinId)).toEqual(['chunky'])
  })
})

describe('top-movers cache invalidation', () => {
  let fake: ReturnType<typeof fakeRedisStore>

  beforeEach(() => {
    vi.clearAllMocks()
    fake = fakeRedisStore()
    redisGet.mockImplementation(fake.get)
    redisSet.mockImplementation(fake.set)
    redisKeys.mockImplementation(fake.keys)
    redisDel.mockImplementation(fake.del)

    skinFindMany.mockResolvedValue([
      {
        id: 'skin-1',
        marketHashName: 'AK-47 | Redline (Field-Tested)',
        name: 'AK-47 | Redline',
        iconUrl: 'http://img/1.png',
        // Two agreeing quotes: the minimum a skin needs to be eligible at all.
        price: { lowestPrice: 10, skinportPrice: 10, csgoMarketPrice: 11, waxpeerPrice: null, updatedAt: new Date() },
      },
    ])
    // A reference price far enough from `lowestPrice` to clear the minimum
    // absolute move comfortably, so the writer actually has something to cache
    // and this test fails for cache reasons rather than threshold ones.
    priceHistoryFindMany.mockResolvedValue([{ skinId: 'skin-1', price: 4 }])
  })

  it('removes every key the writer produced, whatever shape they are', async () => {
    const service = new PriceService()
    await service.getTopMovers('rising', 20)
    await service.getTopMovers('falling', 20)

    // Guards the test itself: if the writer ever stops caching, the assertion
    // below would pass on an empty store and prove nothing.
    expect(fake.store.size).toBe(2)

    await invalidateTopMoversCache()

    expect([...fake.store.keys()]).toEqual([])
  })

  it('leaves caches owned by anything else alone', async () => {
    fake.store.set('prices:skin-1', { lowestPrice: 10 })
    await new PriceService().getTopMovers('rising', 20)

    await invalidateTopMoversCache()

    expect([...fake.store.keys()]).toEqual(['prices:skin-1'])
  })

  it('is a no-op when nothing is cached', async () => {
    await expect(invalidateTopMoversCache()).resolves.toBe(0)
    expect(redisDel).not.toHaveBeenCalled()
  })
})

// The root cause was never the key format itself — it was that four files each
// spelled the key out by hand, so changing one left the other three deleting a
// key nobody wrote. Keeping the literal in a single module is the actual fix;
// this is what stops it drifting apart again.
describe('top-movers key ownership', () => {
  const CACHE_MODULE = join('src', 'services', 'prices.ts')

  function sourceFiles(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) return sourceFiles(full)
      if (!entry.name.endsWith('.ts') || entry.name.endsWith('.test.ts')) return []
      // src/test holds test doubles, not shipped code.
      return full.includes(join('src', 'test')) ? [] : [full]
    })
  }

  it('is spelled out in exactly one module — everyone else calls the invalidator', () => {
    // Matches a cache key (`top-movers:rising:20`), not the public route path
    // `/skins/top-movers`, which is a different thing that happens to read the
    // same up to the colon.
    const CACHE_KEY_LITERAL = /top-movers:/

    const offenders = sourceFiles(join(process.cwd(), 'src'))
      .filter((file) => CACHE_KEY_LITERAL.test(readFileSync(file, 'utf8')))
      .map((file) => relative(process.cwd(), file))
      .filter((file) => file !== CACHE_MODULE)

    expect(offenders).toEqual([])
  })
})
