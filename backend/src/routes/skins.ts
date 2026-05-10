import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'
import { fetchDMarketLivePrice, fetchSkinportLivePrice } from '../jobs/populatePrices'

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

    // Normalised search: strip every non-alphanumeric char from both sides
    // so "awp dragonlore" matches "AWP | Dragon Lore" (the | and spaces vanish).
    if (q) {
      const normalisedQ = q.toLowerCase().replace(/[^a-z0-9]/g, '')
      if (normalisedQ.length > 0) {
        const pattern = `%${normalisedQ}%`
        const matches = await prisma.$queryRaw<{ id: string }[]>`
          SELECT id FROM "Skin"
          WHERE regexp_replace(lower("marketHashName"), '[^a-z0-9]', '', 'g') LIKE ${pattern}
             OR regexp_replace(lower("name"), '[^a-z0-9]', '', 'g') LIKE ${pattern}
        `
        const matchedIds = matches.map((r) => r.id)
        if (matchedIds.length === 0) {
          return { data: [], pagination: { page, limit, total: 0, pages: 0 } }
        }
        conditions.push({ id: { in: matchedIds } })
      }
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
    // When sorting by price, skip skins with no lowestPrice (NULL sorts first in Postgres DESC)
    if (sort === 'price_asc' || sort === 'price_desc') {
      conditions.push({ price: { is: { lowestPrice: { not: null } } } })
    }

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

    // Always recompute priceChange24h live from PriceHistory (same logic as /skins/:id)
    // so values shown in search/home always match what the detail screen shows.
    // The DB-cached column from the bulk job is stale between runs; prices can move in the interim.
    const skinIds = skins.map((s: any) => s.id as string)
    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const oldPrices = await prisma.priceHistory.findMany({
      where: { skinId: { in: skinIds }, timestamp: { lte: dayAgo } },
      orderBy: { timestamp: 'desc' },
      distinct: ['skinId'],
      select: { skinId: true, price: true },
    })
    const oldPriceMap = new Map(oldPrices.map((p) => [p.skinId, p.price]))

    const enrichedSkins = skins.map((s: any) => {
      if (!s.price) return s
      const oldPrice = oldPriceMap.get(s.id)
      const currentPrice = s.price.lowestPrice
      const priceChange24h =
        oldPrice != null && currentPrice != null && oldPrice > 0
          ? ((currentPrice - oldPrice) / oldPrice) * 100
          : s.price.priceChange24h ?? null  // fall back to bulk-job cache if no history entry
      return { ...s, price: { ...s.price, priceChange24h } }
    })

    return { data: enrichedSkins, pagination: { page, limit, total, pages: Math.ceil(total / limit) } }
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

    const skin = await prisma.skin.findUnique({ where: { id: skinId }, include: { price: true } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    // Live price lookups — all in USD for consistency, plus Skinport in EUR for display.
    // Skinport.com shows prices in EUR natively; fetching EUR avoids static-rate mismatch.
    const [liveSkinportUsd, liveSkinportEur, liveDmarketUsd] = await Promise.all([
      fetchSkinportLivePrice(skin.marketHashName, 'USD'),
      fetchSkinportLivePrice(skin.marketHashName, 'EUR'),
      fetchDMarketLivePrice(skin.marketHashName, 'USD'),
    ])

    const updatedSkinportUsd  = liveSkinportUsd  ?? skin.price?.skinportPrice   ?? null
    const updatedDmarketUsd   = liveDmarketUsd   ?? skin.price?.dmarketPrice    ?? null
    const updatedCsgoMarket   =                     skin.price?.csgoMarketPrice ?? null
    const updatedSkinportEur  = liveSkinportEur  ?? null   // display-only, not stored

    const lowestPrice = Math.min(
      ...[updatedSkinportUsd, updatedDmarketUsd, updatedCsgoMarket]
        .filter((p): p is number => p != null && p > 0)
    ) || null

    request.log.info(`[DETAIL] ${skinId}: sp=${liveSkinportUsd}, dm=${liveDmarketUsd}, csgom=${updatedCsgoMarket}, lowest=${lowestPrice}`)

    // Always persist live DMarket price when available — this ensures the batch
    // endpoint has fresh prices for next time (avoids rate limiting issues).
    if (skin.price && liveDmarketUsd !== null) {
      await prisma.skinPrice.update({
        where: { skinId },
        data: {
          dmarketPrice: liveDmarketUsd,
          lowestPrice: lowestPrice ?? skin.price.lowestPrice,
          updatedAt: new Date(),
        },
      })
      request.log.info(`[DETAIL] Saved DMarket price ${liveDmarketUsd} for ${skinId}`)
    }

    // 24h % change (USD history)
    const oldEntry = await prisma.priceHistory.findFirst({
      where: { skinId, timestamp: { lte: new Date(Date.now() - 24 * 60 * 60 * 1000) } },
      orderBy: { timestamp: 'desc' },
    })
    const priceChange24h = oldEntry && lowestPrice && oldEntry.price > 0
      ? ((lowestPrice - oldEntry.price) / oldEntry.price) * 100
      : null

    return {
      ...skin,
      price: skin.price
        ? {
            ...skin.price,
            skinportPrice:    updatedSkinportUsd,
            skinportPriceEur: updatedSkinportEur,  // EUR native — shown as-is in the UI
            dmarketPrice:     updatedDmarketUsd,
            lowestPrice,
            priceChange24h,
          }
        : skin.price,
    }
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
