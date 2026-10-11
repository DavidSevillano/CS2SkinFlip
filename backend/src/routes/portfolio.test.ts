import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'
import fastifyJwt from '@fastify/jwt'

const { userFindUnique, skinFindMany, itemCreateMany, itemDeleteMany } = vi.hoisted(() => ({
  userFindUnique: vi.fn(),
  skinFindMany: vi.fn(),
  itemCreateMany: vi.fn(),
  itemDeleteMany: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: {
    user: { findUnique: userFindUnique },
    skin: { findMany: skinFindMany },
    portfolioItem: { createMany: itemCreateMany, deleteMany: itemDeleteMany },
  },
}))

const { portfolioRoutes } = await import('./portfolio')

async function buildTestApp() {
  const app = Fastify()
  await app.register(fastifyJwt, { secret: 'test-secret-at-least-32-characters-long' })
  await app.register(portfolioRoutes)
  return app
}

async function sync(items: Array<{ assetId: string; marketHashName: string }>) {
  const app = await buildTestApp()
  const token = app.jwt.sign({ userId: 'user-1', steamId: 's' })
  return app.inject({
    method: 'POST',
    url: '/portfolio/sync',
    headers: { authorization: `Bearer ${token}` },
    payload: { items },
  })
}

describe('POST /portfolio/sync', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userFindUnique.mockResolvedValue({ id: 'user-1' })
    skinFindMany.mockResolvedValue([
      { id: 'ak', marketHashName: 'AK-47 | Redline (Field-Tested)', price: { lowestPrice: 12.5 } },
    ])
    itemCreateMany.mockResolvedValue({ count: 1 })
    itemDeleteMany.mockResolvedValue({ count: 0 })
  })

  it('adds catalogued items in one statement, priced at the current market', async () => {
    await sync([
      { assetId: '1', marketHashName: 'AK-47 | Redline (Field-Tested)' },
      { assetId: '2', marketHashName: 'Sticker | Not In Catalog' },
    ])

    expect(itemCreateMany).toHaveBeenCalledTimes(1)
    const { data, skipDuplicates } = itemCreateMany.mock.calls[0][0]
    expect(skipDuplicates).toBe(true)
    expect(data).toEqual([
      expect.objectContaining({ userId: 'user-1', skinId: 'ak', assetId: '1', acquirePrice: 12.5 }),
    ])
  })

  // A sold or traded item leaves the inventory, so it must leave the portfolio —
  // otherwise its value keeps counting towards the totals forever.
  it('removes items that are no longer in the inventory', async () => {
    itemDeleteMany.mockResolvedValue({ count: 3 })

    const res = await sync([{ assetId: '1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])

    expect(itemDeleteMany).toHaveBeenCalledWith({
      where: { userId: 'user-1', assetId: { notIn: ['1'] } },
    })
    expect(res.json()).toEqual({ synced: 1, total: 1, removed: 3 })
  })

  // An empty list is far more likely a Steam hiccup than a user who sold
  // everything; wiping the portfolio on it would destroy edited purchase prices.
  it('removes nothing when the inventory comes back empty', async () => {
    await sync([])

    expect(itemDeleteMany).not.toHaveBeenCalled()
  })

  it('refuses an implausibly large payload', async () => {
    const items = Array.from({ length: 5001 }, (_, i) => ({ assetId: String(i), marketHashName: 'x' }))

    const res = await sync(items)

    expect(res.statusCode).toBe(400)
    expect(skinFindMany).not.toHaveBeenCalled()
  })
})
