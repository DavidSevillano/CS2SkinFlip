import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'
import { getSteamPrice } from '../services/steamPrice'
import { env } from '../config/env'

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
  // Stricter per-IP limit on the DB-heavy search: each call runs a raw
  // regexp_replace scan over the ~24k-row Skin table, so it's the easiest
  // route to hammer the database with. Tighter than the global default.
  const searchRateLimit = {
    config: {
      rateLimit: {
        max: env.RATE_LIMIT_SEARCH_MAX,
        timeWindow: env.RATE_LIMIT_WINDOW,
      },
    },
  }

  app.get('/skins', searchRateLimit, async (request, reply) => {
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
    } else if (sort === 'price_asc' || sort === 'price_desc') {
      // Use DB lowestPrice (kept fresh by 2h bulk job) for price-sorted results.
      // Live CSFloat lookups happen in the batch endpoint after Android receives the list.
      const candidates = await prisma.skin.findMany({
        where,
        include: { price: true },
        orderBy: { price: { lowestPrice: sort === 'price_asc' ? 'asc' : 'desc' } },
        take: limit * 2,
      })

      const withFreshPrices = candidates.map(skin => ({
        skin,
        lowestPrice: skin.price?.lowestPrice ?? 0,
      }))

      // Sort by fresh lowest price
      const asc = sort === 'price_asc'
      withFreshPrices.sort((a, b) => {
        return asc ? a.lowestPrice - b.lowestPrice : b.lowestPrice - a.lowestPrice
      })

      // Apply pagination
      skins = withFreshPrices.slice(skip, skip + limit).map(r => r.skin)
    } else {
      // sort === 'name'
      skins = await prisma.skin.findMany({ where, include: { price: true }, skip, take: limit, orderBy: { name: 'asc' } })
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

  app.get('/skins/top-movers', async (request) => {
    const { direction } = request.query as { direction?: string }
    return priceService.getTopMovers(direction === 'falling' ? 'falling' : 'rising', 20)
  })

  // Full bulk export consumed once per run by the static site generator that
  // builds the public SEO site. Only skins with a non-null `lowestPrice` get an
  // indexable page — hence the filter is on the price value, not merely the
  // relation: a SkinPrice row can exist with all three marketplace prices (and
  // thus `lowestPrice`) null, which would otherwise sort FIRST under
  // `ORDER BY lowestPrice DESC` (Postgres NULLS FIRST) and be thin content.
  // Ordered by value so the generator can cap page count by taking a prefix
  // (Cloudflare Pages allows at most 20k files per deployment).
  // Deliberately uncached: called ~once a day, and the ~6MB payload is a poor
  // fit for Upstash.
  app.get('/skins/export', async () => {
    const skins = await prisma.skin.findMany({
      where: { price: { lowestPrice: { not: null } } },
      include: { price: true },
      orderBy: { price: { lowestPrice: 'desc' } },
    })

    return skins.map((s) => ({
      id: s.id,
      marketHashName: s.marketHashName,
      weapon: s.weapon,
      rarity: s.rarity,
      iconUrl: s.iconUrl,
      skinportPrice: s.price!.skinportPrice,
      csgoMarketPrice: s.price!.csgoMarketPrice,
      waxpeerPrice: s.price!.waxpeerPrice,
      lowestPrice: s.price!.lowestPrice,
      priceChange24h: s.price!.priceChange24h,
      updatedAt: s.price!.updatedAt.toISOString(),
    }))
  })

  app.get('/skins/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId }, include: { price: true } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    // 24h % change recomputed from PriceHistory — more accurate than the bulk-job cache.
    const oldEntry = await prisma.priceHistory.findFirst({
      where: { skinId, timestamp: { lte: new Date(Date.now() - 24 * 60 * 60 * 1000) } },
      orderBy: { timestamp: 'desc' },
    })
    const lowest = skin.price?.lowestPrice ?? null
    const priceChange24h = oldEntry && lowest && oldEntry.price > 0
      ? ((lowest - oldEntry.price) / oldEntry.price) * 100
      : (skin.price?.priceChange24h ?? null)

    return {
      ...skin,
      price: skin.price ? { ...skin.price, priceChange24h } : skin.price,
    }
  })

  app.get('/skins/:skinId/steam-price', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId }, select: { marketHashName: true } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    const price = await getSteamPrice(skinId, skin.marketHashName, request.log)
    return { price }
  })

  app.get('/skins/:skinId/price-history', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }
    const { range: rawRange } = request.query as { range?: string }

    const rangeConfig: Record<string, { sinceMs: number; aggregateDaily: boolean }> = {
      '24h': { sinceMs: 24 * 60 * 60 * 1000, aggregateDaily: false },
      '7d':  { sinceMs: 7 * 24 * 60 * 60 * 1000, aggregateDaily: true },
      '30d': { sinceMs: 30 * 24 * 60 * 60 * 1000, aggregateDaily: true },
      '90d': { sinceMs: 90 * 24 * 60 * 60 * 1000, aggregateDaily: true },
    }
    const { sinceMs, aggregateDaily } = rangeConfig[rawRange ?? '24h'] ?? rangeConfig['24h']
    const since = new Date(Date.now() - sinceMs)

    const history = await prisma.priceHistory.findMany({
      where: { skinId, timestamp: { gte: since } },
      orderBy: { timestamp: 'asc' },
      select: { price: true, timestamp: true, source: true },
    })

    if (!aggregateDaily) {
      return history.map((h) => ({ price: h.price, timestamp: h.timestamp.toISOString(), source: h.source }))
    }

    // Para rangos agregados: agrupar por día UTC y emitir dos puntos, el de
    // precio mínimo y el de máximo, ordenados por timestamp. Funciona igual
    // sobre la parte raw reciente (0–14d) que sobre la ya downsampleada
    // (14–120d), por lo que 30d y 90d quedan homogéneos. `history` viene
    // ascendente, así que la iteración del Map queda ascendente por día.
    type Point = { price: number; timestamp: string; source: string }
    const byDay = new Map<string, Point[]>()
    for (const h of history) {
      const dayKey = h.timestamp.toISOString().slice(0, 10)
      const point: Point = { price: h.price, timestamp: h.timestamp.toISOString(), source: h.source }
      const arr = byDay.get(dayKey)
      if (arr) arr.push(point)
      else byDay.set(dayKey, [point])
    }

    const result: Point[] = []
    for (const points of byDay.values()) {
      let min = points[0]
      let max = points[0]
      for (const p of points) {
        if (p.price < min.price) min = p
        if (p.price > max.price) max = p
      }
      if (min === max) {
        result.push(min) // día plano o un solo punto
      } else {
        result.push(
          ...[min, max].sort((a, b) => a.timestamp.localeCompare(b.timestamp)),
        )
      }
    }
    return result
  })
}
