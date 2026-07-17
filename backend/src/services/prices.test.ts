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
        price: { lowestPrice: 10, skinportPrice: 10, csgoMarketPrice: null, waxpeerPrice: null, updatedAt: new Date() },
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
        price: { lowestPrice: 10, skinportPrice: 10, csgoMarketPrice: null, waxpeerPrice: null, updatedAt: new Date() },
      },
    ])
    // A reference price far enough from `lowestPrice` to clear the $0.25
    // quality filter, so the writer actually has something to cache.
    priceHistoryFindMany.mockResolvedValue([{ skinId: 'skin-1', price: 8 }])
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
