import { describe, it, expect, vi, beforeEach } from 'vitest'

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

const { redisGet, redisSet } = vi.hoisted(() => ({ redisGet: vi.fn(), redisSet: vi.fn() }))
vi.mock('../redis/client', () => ({
  redis: { get: redisGet, set: redisSet },
  CACHE_TTL: { TOP_MOVERS: 900, SKIN_PRICES: 300 },
}))

const { PriceService } = await import('./prices')

const TWELVE_HOURS_MS = 12 * 60 * 60 * 1000

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
    expect(after - lte.getTime()).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000)
    expect(lte.getTime() - gte.getTime()).toBe(TWELVE_HOURS_MS)
    expect(before - gte.getTime()).toBeGreaterThanOrEqual(36 * 60 * 60 * 1000)
  })
})
