import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'

const { skinFindMany, skinCount, queryRaw } = vi.hoisted(() => ({
  skinFindMany: vi.fn(),
  skinCount: vi.fn(),
  queryRaw: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: {
    skin: { findMany: skinFindMany, count: skinCount },
    $queryRaw: queryRaw,
  },
}))

// `$queryRaw` is a tagged template: the literal fragments arrive first and the
// interpolated values after, so the window bounds are positional rather than
// named. In query order: windowStart (gte) then dayAgo (lte).
function queryRawDates(call: unknown[]): Date[] {
  return call.slice(1).filter((v): v is Date => v instanceof Date)
}

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

// Same class of bug as the Render OOM that `populatePrices.test.ts` guards, in the
// other half of the codebase that reads a 24h reference price. The search route
// resolved `priceChange24h` with an unbounded `timestamp: { lte: dayAgo }`, so
// `distinct: ['skinId']` — which Prisma cannot push down to Postgres under a
// timestamp `orderBy` — materialised the *entire retained history* of every skin
// on the page (~130 rows each over the 90d retention) to use one row each.
//
// It never errored and never showed a wrong number, it just moved ~130x more bytes
// out of the database than it needed, on the app's single hottest route. Bounding
// it also makes the window agree with the one the bulk job uses, which is the
// whole reason `CHANGE_REFERENCE_WINDOW_MS` is a shared constant.
describe('GET /skins — 24h-change reference query', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    skinCount.mockResolvedValue(1)
    skinFindMany.mockResolvedValue([row('ak-47-redline-field-tested', 9.99)])
    queryRaw.mockResolvedValue([])
  })

  it('bounds the reference window on the lower end, not just the upper', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins?limit=50' })

    expect(res.statusCode).toBe(200)
    expect(queryRaw).toHaveBeenCalledTimes(1)
    const [gte, lte] = queryRawDates(queryRaw.mock.calls[0])
    expect(gte).toBeInstanceOf(Date)
    expect(lte).toBeInstanceOf(Date)
  })

  it('uses the same window width as the bulk job, so both agree on the reference', async () => {
    const { CHANGE_REFERENCE_WINDOW_MS } = await import('../config/priceHistory')
    const app = await buildTestApp()

    const before = Date.now()
    await app.inject({ method: 'GET', url: '/skins?limit=50' })
    const after = Date.now()

    const [gte, lte] = queryRawDates(queryRaw.mock.calls[0])

    // Both bounds derive from a Date.now() taken inside the handler, somewhere in
    // [before, after] — so `after` is the only safe reference for a lower-bound
    // assertion, exactly as in populatePrices.test.ts.
    expect(after - lte.getTime()).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000)
    expect(before - lte.getTime()).toBeLessThanOrEqual(24 * 60 * 60 * 1000)
    expect(lte.getTime() - gte.getTime()).toBe(CHANGE_REFERENCE_WINDOW_MS)
  })

  it('still falls back to the bulk-job column when the window holds no reference', async () => {
    queryRaw.mockResolvedValue([])
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins?limit=50' })

    // row() seeds priceChange24h: 3.2 on the price relation — narrowing the window
    // must not turn a known change into null.
    expect(res.json().data[0].price.priceChange24h).toBe(3.2)
  })
})

// Price-sorted search used to fetch `limit * 2` candidates and then slice at the
// requested offset, so page 3 sliced [100, 150) out of a 100-element array. Every
// page from the third on came back empty, which a client reads as "no more
// results" rather than as a bug.
describe('GET /skins — price-sorted pagination', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    skinCount.mockResolvedValue(500)
    skinFindMany.mockResolvedValue([row('skin-a', 120), row('skin-b', 80)])
    queryRaw.mockResolvedValue([])
  })

  it('returns results on the third page', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/skins?sort=price_desc&page=3&limit=50' })

    expect(res.statusCode).toBe(200)
    expect(res.json().data).toHaveLength(2)
  })

  it('pushes the offset down to the database instead of slicing in memory', async () => {
    const app = await buildTestApp()

    await app.inject({ method: 'GET', url: '/skins?sort=price_desc&page=3&limit=50' })

    const args = skinFindMany.mock.calls[0][0]
    expect(args.skip).toBe(100)
    expect(args.take).toBe(50)
  })

  it('asks the database to do the ordering', async () => {
    const app = await buildTestApp()

    await app.inject({ method: 'GET', url: '/skins?sort=price_asc&limit=50' })

    // The in-memory re-sort this replaced was a no-op over an already-ordered
    // set, and its `?? 0` null handling could never fire: rows with a null
    // lowestPrice are excluded by the where clause whenever this sort is active.
    expect(skinFindMany.mock.calls[0][0].orderBy).toEqual({ price: { lowestPrice: 'asc' } })
  })
})
