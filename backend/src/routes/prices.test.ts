import { describe, it, expect, vi, beforeEach } from 'vitest'

// Reference points written after CSFloat joined lowestPrice, so the 24h change
// is a plain comparison with lowestPrice (see CSFLOAT_IN_LOWEST_FROM).
const REF_AT = new Date('2026-10-12T00:00:00Z')
import Fastify from 'fastify'

const { skinPriceFindMany, skinFindUnique, queryRaw } = vi.hoisted(() => ({
  skinPriceFindMany: vi.fn(),
  skinFindUnique: vi.fn(),
  queryRaw: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: {
    skinPrice: { findMany: skinPriceFindMany },
    skin: { findUnique: skinFindUnique },
    $queryRaw: queryRaw,
  },
}))

vi.mock('../redis/client', () => ({
  redis: { get: vi.fn(), set: vi.fn(), del: vi.fn(), keys: vi.fn() },
  CACHE_TTL: { TOP_MOVERS: 900, SKIN_PRICES: 300 },
}))

const { priceRoutes } = await import('./prices')

async function buildTestApp() {
  const app = Fastify()
  await app.register(priceRoutes)
  return app
}

function priceRow(skinId: string, lowestPrice: number, priceChange24h: number | null = null) {
  return { skinId, skinportPrice: lowestPrice, csgoMarketPrice: null, waxpeerPrice: null, lowestPrice, priceChange24h }
}

describe('GET /prices/batch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    queryRaw.mockResolvedValue([])
  })

  // The watchlist refreshes with this one call, so it must carry the 24h change
  // too — otherwise the screen keeps showing the change from the day it was added.
  it('includes the 24h change, computed against the reference price from a day ago', async () => {
    skinPriceFindMany.mockResolvedValue([priceRow('ak', 110)])
    queryRaw.mockResolvedValue([{ skinId: 'ak', price: 100, at: REF_AT }])
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/prices/batch?ids=ak' })

    expect(res.statusCode).toBe(200)
    expect(res.json().ak).toEqual({
      skinportPrice: 110,
      csgoMarketPrice: null,
      waxpeerPrice: null,
      lowestPrice: 110,
      priceChange24h: 10,
    })
  })

  it('falls back to the stored change when there is no reference price', async () => {
    skinPriceFindMany.mockResolvedValue([priceRow('ak', 110, 4.2)])
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/prices/batch?ids=ak' })

    expect(res.json().ak.priceChange24h).toBe(4.2)
  })

  it('looks reference prices up in one bounded query for all requested skins', async () => {
    skinPriceFindMany.mockResolvedValue([priceRow('a', 1), priceRow('b', 2)])
    const app = await buildTestApp()

    await app.inject({ method: 'GET', url: '/prices/batch?ids=a,b' })

    expect(queryRaw).toHaveBeenCalledTimes(1)
  })

  it('caps a request at 50 skins', async () => {
    skinPriceFindMany.mockResolvedValue([])
    const ids = Array.from({ length: 80 }, (_, i) => `s${i}`).join(',')
    const app = await buildTestApp()

    await app.inject({ method: 'GET', url: `/prices/batch?ids=${ids}` })

    expect(skinPriceFindMany.mock.calls[0][0].where.skinId.in).toHaveLength(50)
  })

  it('requires ids', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/prices/batch' })

    expect(res.statusCode).toBe(400)
  })
})
