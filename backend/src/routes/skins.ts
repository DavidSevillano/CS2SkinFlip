import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'
import { SteamService } from '../services/steam'

const priceService = new PriceService()
const steamService = new SteamService()

const searchQuerySchema = z.object({
  q: z.string().optional(),
  weapon: z.string().optional(),
  rarity: z.string().optional(),
  minPrice: z.coerce.number().optional(),
  maxPrice: z.coerce.number().optional(),
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(200).default(50),
  sort: z.enum(['name', 'price_asc', 'price_desc', 'random']).default('random'),
})

async function fetchAndStorePrices(skins: Array<{ id: string; marketHashName: string }>) {
  await Promise.all(
    skins.map(async (skin) => {
      try {
        const { lowestPrice, volume } = await steamService.getMarketPrice(skin.marketHashName)
        if (lowestPrice && lowestPrice > 0) {
          await prisma.skinPrice.upsert({
            where: { skinId: skin.id },
            update: { steamPrice: lowestPrice, lowestPrice, volume24h: volume, updatedAt: new Date() },
            create: { skinId: skin.id, steamPrice: lowestPrice, lowestPrice, volume24h: volume },
          })
        }
      } catch { /* ignore individual failures */ }
    })
  )
}

export const skinRoutes: FastifyPluginAsync = async (app) => {
  app.get('/skins', async (request, reply) => {
    const params = searchQuerySchema.safeParse(request.query)
    if (!params.success) {
      return reply.status(400).send({ error: 'Invalid query parameters', details: params.error.flatten() })
    }

    const { q, weapon, rarity, minPrice, maxPrice, page, limit, sort } = params.data
    const skip = (page - 1) * limit
    const hasQuery = !!(q || weapon || rarity)

    const where: Parameters<typeof prisma.skin.findMany>[0]['where'] = {}

    if (q) {
      where.OR = [
        { name: { contains: q, mode: 'insensitive' } },
        { marketHashName: { contains: q, mode: 'insensitive' } },
      ]
    }
    if (weapon) where.weapon = { equals: weapon, mode: 'insensitive' }
    if (rarity) where.rarity = { equals: rarity, mode: 'insensitive' }
    if (minPrice !== undefined || maxPrice !== undefined) {
      where.price = { lowestPrice: {} }
      if (minPrice !== undefined) (where.price as any).lowestPrice.gte = minPrice
      if (maxPrice !== undefined) (where.price as any).lowestPrice.lte = maxPrice
    }

    // Without a search query: only show skins that already have a price
    if (!hasQuery) {
      where.price = { ...((where.price as any) ?? {}), isNot: null }
    }

    const total = await prisma.skin.count({ where })

    let skins: any[]

    if (sort === 'random') {
      const allIds = await prisma.skin.findMany({ where, select: { id: true } })
      const shuffled = allIds.sort(() => Math.random() - 0.5).slice(skip, skip + limit)
      const ids = shuffled.map((s) => s.id)
      skins = await prisma.skin.findMany({ where: { id: { in: ids } }, include: { price: true } })
    } else {
      const orderBy: Parameters<typeof prisma.skin.findMany>[0]['orderBy'] =
        sort === 'price_asc'  ? { price: { lowestPrice: 'asc' } }  :
        sort === 'price_desc' ? { price: { lowestPrice: 'desc' } } :
        { name: 'asc' }
      skins = await prisma.skin.findMany({ where, include: { price: true }, skip, take: limit, orderBy })
    }

    // When searching: fetch prices on-demand for results that have none
    if (hasQuery) {
      const unpriced = skins.filter((s) => !s.price).slice(0, 20) // max 20 on-demand fetches
      if (unpriced.length > 0) {
        await fetchAndStorePrices(unpriced)
        // Reload with fresh prices
        const ids = skins.map((s) => s.id)
        skins = await prisma.skin.findMany({ where: { id: { in: ids } }, include: { price: true } })
      }
    }

    return { data: skins, pagination: { page, limit, total, pages: Math.ceil(total / limit) } }
  })

  app.get('/skins/weapons', async () => {
    const weapons = await prisma.skin.findMany({
      select: { weapon: true },
      distinct: ['weapon'],
      orderBy: { weapon: 'asc' },
    })
    return weapons.map((w) => w.weapon)
  })

  app.get('/skins/top-movers', async () => {
    return priceService.getTopMovers(20)
  })

  app.get('/skins/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    let skin = await prisma.skin.findUnique({ where: { id: skinId }, include: { price: true } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    // Fetch price on-demand if missing
    if (!skin.price) {
      await fetchAndStorePrices([{ id: skin.id, marketHashName: skin.marketHashName }])
      skin = await prisma.skin.findUnique({ where: { id: skinId }, include: { price: true } }) ?? skin
    }

    return skin
  })

  app.get('/skins/:skinId/price-history', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }
    const { days = '30' } = request.query as { days?: string }

    const daysNum = Math.min(parseInt(days, 10) || 30, 365)
    const since = new Date(Date.now() - daysNum * 24 * 60 * 60 * 1000)

    const history = await prisma.priceHistory.findMany({
      where: { skinId, timestamp: { gte: since } },
      orderBy: { timestamp: 'asc' },
      select: { price: true, timestamp: true, source: true },
    })

    return history.map((h) => ({ price: h.price, timestamp: h.timestamp.toISOString(), source: h.source }))
  })
}
