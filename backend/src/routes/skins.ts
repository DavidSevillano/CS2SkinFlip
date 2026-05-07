import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'

const priceService = new PriceService()

const searchQuerySchema = z.object({
  q: z.string().optional(),
  weapon: z.string().optional(),
  rarity: z.string().optional(),
  minPrice: z.coerce.number().optional(),
  maxPrice: z.coerce.number().optional(),
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(50).default(20),
})

export const skinRoutes: FastifyPluginAsync = async (app) => {
  app.get('/skins', async (request, reply) => {
    const params = searchQuerySchema.safeParse(request.query)
    if (!params.success) {
      return reply.status(400).send({ error: 'Invalid query parameters', details: params.error.flatten() })
    }

    const { q, weapon, rarity, minPrice, maxPrice, page, limit } = params.data
    const skip = (page - 1) * limit

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

    const [skins, total] = await Promise.all([
      prisma.skin.findMany({ where, include: { price: true }, skip, take: limit, orderBy: { name: 'asc' } }),
      prisma.skin.count({ where }),
    ])

    return {
      data: skins,
      pagination: { page, limit, total, pages: Math.ceil(total / limit) },
    }
  })

  app.get('/skins/top-movers', async (_request, reply) => {
    const topMovers = await priceService.getTopMovers(20)
    return topMovers
  })

  app.get('/skins/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({
      where: { id: skinId },
      include: { price: true },
    })

    if (!skin) return reply.status(404).send({ error: 'Skin not found' })
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
