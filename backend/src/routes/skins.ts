import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'
import { change24h, fetchReferencePrices } from '../services/priceReference'
import { env } from '../config/env'
import { requireSecretHeader } from '../middleware/requireSecretHeader'
import { WEAPON_CATEGORIES, WEAPON_CATEGORY_KEYS } from '../config/weaponCategories'

const priceService = new PriceService()

const searchQuerySchema = z.object({
  q: z.string().optional(),
  weapon: z.string().optional(),
  category: z.enum(WEAPON_CATEGORY_KEYS).optional(),
  rarity: z.string().optional(),
  wear: z.string().optional(),
  statTrak: z.coerce.boolean().optional(),
  minPrice: z.coerce.number().optional(),
  maxPrice: z.coerce.number().optional(),
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(200).default(50),
  sort: z.enum(['name', 'price_asc', 'price_desc', 'popular', 'random']).default('random'),
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

    const { q, weapon, category, rarity, wear, statTrak, minPrice, maxPrice, page, limit, sort } = params.data
    const skip = (page - 1) * limit
    const hasQuery = !!(q || weapon || category || rarity || wear || statTrak)

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
    if (category) conditions.push({ weapon: { in: [...WEAPON_CATEGORIES[category]] } })
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
      // Randomise by starting at a random offset, not by pulling every matching
      // id across the wire to shuffle in memory. `sort` defaults to 'random' and
      // any filter at all takes this branch, so the old version made a broad
      // search — `wear=Field-Tested`, say — ship thousands of ids per request to
      // return `limit` of them. On a metered connection that is the single most
      // expensive thing this API did, and it bought nothing: `skip` was applied
      // to a freshly shuffled array on every call, so paging returned
      // overlapping random slices rather than a stable sequence.
      //
      // `total` is already counted above, so choosing the window is free.
      const offset = total > limit ? Math.floor(Math.random() * (total - limit + 1)) : 0
      skins = await prisma.skin.findMany({
        where,
        include: { price: true },
        skip: offset,
        take: limit,
      })
    } else if (sort === 'price_asc' || sort === 'price_desc') {
      // Sorted on the DB's lowestPrice, which the 6h bulk job keeps fresh.
      //
      // This used to fetch `limit * 2` candidates, re-sort them in memory, then
      // slice at `skip` — so page 3 sliced [100, 150) out of a 100-element array
      // and every page from there on came back empty, reading to the client as
      // "no more results". The re-sort was a no-op besides: the database had
      // already ordered by the same column, and the `?? 0` null handling it
      // carried could never fire because the condition above excludes rows with
      // a null lowestPrice whenever this sort is active.
      skins = await prisma.skin.findMany({
        where,
        include: { price: true },
        orderBy: { price: { lowestPrice: sort === 'price_asc' ? 'asc' : 'desc' } },
        skip,
        take: limit,
      })
    } else if (sort === 'popular') {
      // Most CS:GO Market listings first (`volume24h`, the only liquidity signal
      // we have): the skins that actually trade, where a price-descending browse
      // opened on one-listing knives at $40k+. Skins it doesn't list go last;
      // `id` breaks ties so paging is stable.
      skins = await prisma.skin.findMany({
        where,
        include: { price: true },
        orderBy: [{ price: { volume24h: { sort: 'desc', nulls: 'last' } } }, { id: 'asc' }],
        skip,
        take: limit,
      })
    } else {
      // sort === 'name'
      skins = await prisma.skin.findMany({ where, include: { price: true }, skip, take: limit, orderBy: { name: 'asc' } })
    }

    // Always recompute priceChange24h from PriceHistory so search, home and the
    // detail screen agree; the bulk job's cached column is the fallback when the
    // reference window holds no point. See fetchReferencePrices.
    const references = await fetchReferencePrices(skins.map((s: any) => s.id as string))
    const enrichedSkins = skins.map((s: any) => {
      if (!s.price) return s
      const priceChange24h =
        change24h(s.price, references.get(s.id)) ?? s.price.priceChange24h ?? null
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
  //
  // Which is exactly why it must not be public: ~6 MB through the 100/min global
  // limit is ~600 MB/min from a single IP, the whole Neon transfer allowance in
  // minutes. Only the generator holds EXPORT_SECRET; without it set the route
  // doesn't exist (and `/skins/export` falls through to `/skins/:skinId` → 404).
  const exportSecret = env.EXPORT_SECRET
  if (!exportSecret) {
    app.log.warn('[Skins] EXPORT_SECRET not set — /skins/export disabled; the SEO site cannot refresh')
  } else {
    await app.register(async (guarded) => {
      guarded.addHook('onRequest', requireSecretHeader('X-Export-Secret', exportSecret))
      guarded.get('/skins/export', exportHandler)
    })
  }

  async function exportHandler() {
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
      csfloatPrice: s.price!.csfloatPrice,
      waxpeerPrice: s.price!.waxpeerPrice,
      lowestPrice: s.price!.lowestPrice,
      priceChange24h: s.price!.priceChange24h,
      updatedAt: s.price!.updatedAt.toISOString(),
    }))
  }

  app.get('/skins/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId }, include: { price: true } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    // Same reference and fallback as the lists, so opening a skin from search
    // shows the change the list just showed.
    const references = await fetchReferencePrices([skinId])
    const priceChange24h =
      (skin.price ? change24h(skin.price, references.get(skinId)) : null) ?? skin.price?.priceChange24h ?? null

    return {
      ...skin,
      price: skin.price ? { ...skin.price, priceChange24h } : skin.price,
    }
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
    // sobre la parte raw reciente que sobre la ya downsampleada — donde sólo hay
    // un punto al día, mínimo y máximo coinciden y se emite uno solo — por lo que
    // 30d y 90d quedan homogéneos. Las fronteras exactas las fija
    // `cleanupPriceHistory.ts`, que es quien manda; no las repitas aquí (este
    // comentario decía 0–14d / 14–120d, y ambas cifras habían quedado obsoletas).
    // `history` viene ascendente, así que la iteración del Map queda ascendente.
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
