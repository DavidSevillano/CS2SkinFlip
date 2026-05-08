import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'

const priceService = new PriceService()

const searchQuerySchema = z.object({
  q: z.string().optional(),
  weapon: z.string().optional(),
  rarity: z.string().optional(),
  wear: z.string().optional(),
  statTrak: z.coerce.boolean().optional(),
  minPrice: z.coerce.number().optional(),
  maxPrice: z.coerce.number().optional(),
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(200).default(50),
  sort: z.enum(['name', 'price_asc', 'price_desc', 'random']).default('random'),
})


export const skinRoutes: FastifyPluginAsync = async (app) => {
  app.get('/skins', async (request, reply) => {
    const params = searchQuerySchema.safeParse(request.query)
    if (!params.success) {
      return reply.status(400).send({ error: 'Invalid query parameters', details: params.error.flatten() })
    }

    const { q, weapon, rarity, wear, statTrak, minPrice, maxPrice, page, limit, sort } = params.data
    const skip = (page - 1) * limit
    const hasQuery = !!(q || weapon || rarity || wear || statTrak)

    // Build all filters as AND conditions to avoid field-merging conflicts
    const conditions: any[] = []

    if (q) {
      conditions.push({ OR: [
        { name: { contains: q, mode: 'insensitive' } },
        { marketHashName: { contains: q, mode: 'insensitive' } },
      ]})
    }
    if (weapon) conditions.push({ weapon: { equals: weapon, mode: 'insensitive' } })
    if (rarity) conditions.push({ rarity: { equals: rarity, mode: 'insensitive' } })
    // wear is stored as "(Field-Tested)" suffix in marketHashName
    if (wear) conditions.push({ marketHashName: { contains: `(${wear})`, mode: 'insensitive' } })
    // use 'StatTrak' without ™ to avoid unicode matching issues
    if (statTrak) conditions.push({ marketHashName: { contains: 'StatTrak', mode: 'insensitive' } })
    if (minPrice !== undefined || maxPrice !== undefined) {
      const priceFilter: any = {}
      if (minPrice !== undefined) priceFilter.gte = minPrice
      if (maxPrice !== undefined) priceFilter.lte = maxPrice
      conditions.push({ price: { lowestPrice: priceFilter } })
    }
    // Without a search query: only show skins that already have a price
    if (!hasQuery) conditions.push({ price: { isNot: null } })

    const where: NonNullable<Parameters<typeof prisma.skin.findMany>[0]>['where'] =
      conditions.length > 0 ? { AND: conditions } : {}

    const total = await prisma.skin.count({ where })

    let skins: any[]

    if (sort === 'random' && !hasQuery) {
      // Browse: most valuable skins first, paginated
      skins = await prisma.skin.findMany({
        where,
        include: { price: true },
        orderBy: { price: { lowestPrice: 'desc' } },
        skip,
        take: limit,
      })
    } else if (sort === 'random') {
      const allIds = await prisma.skin.findMany({ where, select: { id: true } })
      const shuffled = allIds.sort(() => Math.random() - 0.5).slice(skip, skip + limit)
      const ids = shuffled.map((s) => s.id)
      skins = await prisma.skin.findMany({ where: { id: { in: ids } }, include: { price: true } })
    } else {
      const orderBy: NonNullable<Parameters<typeof prisma.skin.findMany>[0]>['orderBy'] =
        sort === 'price_asc'  ? { price: { lowestPrice: 'asc' } }  :
        sort === 'price_desc' ? { price: { lowestPrice: 'desc' } } :
        { name: 'asc' }
      skins = await prisma.skin.findMany({ where, include: { price: true }, skip, take: limit, orderBy })
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
