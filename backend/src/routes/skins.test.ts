import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'

const { skinFindMany, skinFindUnique } = vi.hoisted(() => ({
  skinFindMany: vi.fn(),
  skinFindUnique: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: { skin: { findMany: skinFindMany, findUnique: skinFindUnique } },
}))

// `skins.ts` imports `env`, whose module body calls process.exit(1) when the
// real env vars are absent (they are, under Vitest). Stub the two fields it reads.
vi.mock('../config/env', () => ({
  env: { RATE_LIMIT_SEARCH_MAX: 30, RATE_LIMIT_WINDOW: '1 minute' },
}))

// The route module constructs a PriceService, which pulls in redis. Stub it so
// importing the routes has no side effects.
vi.mock('../services/prices', () => ({
  PriceService: class {
    async getTopMovers() {
      return []
    }
  },
}))

const { getSteamPrice } = vi.hoisted(() => ({ getSteamPrice: vi.fn() }))
vi.mock('../services/steamPrice', () => ({ getSteamPrice }))

const { skinRoutes } = await import('./skins')

async function buildTestApp() {
  const app = Fastify()
  await app.register(skinRoutes)
  return app
}

function row(id: string, lowestPrice: number | null) {
  return {
    id,
    marketHashName: `${id} hash`,
    weapon: 'AK-47',
    rarity: 'Classified',
    iconUrl: `https://cdn.example.com/${id}.png`,
    price: {
      skinportPrice: 12.5,
      csgoMarketPrice: 9.99,
      waxpeerPrice: null,
      lowestPrice,
      priceChange24h: 3.2,
      updatedAt: new Date('2026-07-01T10:00:00.000Z'),
    },
  }
}

describe('GET /skins/export', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('flattens each skin and its price into a single object', async () => {
    skinFindMany.mockResolvedValue([row('ak-47-redline-field-tested', 9.99)])
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins/export' })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual([
      {
        id: 'ak-47-redline-field-tested',
        marketHashName: 'ak-47-redline-field-tested hash',
        weapon: 'AK-47',
        rarity: 'Classified',
        iconUrl: 'https://cdn.example.com/ak-47-redline-field-tested.png',
        skinportPrice: 12.5,
        csgoMarketPrice: 9.99,
        waxpeerPrice: null,
        lowestPrice: 9.99,
        priceChange24h: 3.2,
        updatedAt: '2026-07-01T10:00:00.000Z',
      },
    ])
  })

  it('queries only priced skins, ordered by lowestPrice descending', async () => {
    skinFindMany.mockResolvedValue([])
    const app = await buildTestApp()

    await app.inject({ method: 'GET', url: '/skins/export' })

    expect(skinFindMany).toHaveBeenCalledWith({
      where: { price: { lowestPrice: { not: null } } },
      include: { price: true },
      orderBy: { price: { lowestPrice: 'desc' } },
    })
  })

  it('excludes skins whose price row exists but has no lowestPrice', async () => {
    skinFindMany.mockResolvedValue([])
    const app = await buildTestApp()

    await app.inject({ method: 'GET', url: '/skins/export' })

    const where = skinFindMany.mock.calls[0][0].where
    expect(where).toEqual({ price: { lowestPrice: { not: null } } })
  })

  it('returns an empty array when no skins have prices', async () => {
    skinFindMany.mockResolvedValue([])
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins/export' })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual([])
  })

  it('no longer exposes the superseded /skins/sitemap route', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins/sitemap' })

    // Falls through to /skins/:skinId, whose handler calls findUnique — absent from
    // our prisma mock. Anything but a 200 export payload proves the route is gone.
    expect(res.statusCode).not.toBe(200)
  })
})

describe('GET /skins/:skinId/steam-price', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('404s when the skin does not exist', async () => {
    skinFindUnique.mockResolvedValue(null)
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins/missing-skin/steam-price' })

    expect(res.statusCode).toBe(404)
    expect(getSteamPrice).not.toHaveBeenCalled()
  })

  it('returns the price from the Steam price service', async () => {
    skinFindUnique.mockResolvedValue({ marketHashName: 'AK-47 | Redline (Field-Tested)' })
    getSteamPrice.mockResolvedValue(32.39)
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins/ak-47-redline-field-tested/steam-price' })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ price: 32.39 })
    expect(getSteamPrice).toHaveBeenCalledWith(
      'ak-47-redline-field-tested',
      'AK-47 | Redline (Field-Tested)',
      expect.anything(),
    )
  })

  it('returns a null price (not an error) when Steam has no listing', async () => {
    skinFindUnique.mockResolvedValue({ marketHashName: 'Some Unlisted Skin' })
    getSteamPrice.mockResolvedValue(null)
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins/some-unlisted-skin/steam-price' })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ price: null })
  })
})
