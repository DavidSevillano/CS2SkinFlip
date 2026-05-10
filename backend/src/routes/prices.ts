import { FastifyPluginAsync } from 'fastify'
import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { PriceService } from '../services/prices'
import { fetchSkinportLivePrice, fetchDMarketLivePrice } from '../jobs/populatePrices'

const priceService = new PriceService()

export const priceRoutes: FastifyPluginAsync = async (app) => {
  /**
   * GET /prices/batch?ids=id1,id2,...
   *
   * Returns accurate prices for up to 50 skins in a single round-trip.
   *
   * - Skinport:     in-memory map (refreshed every 30 min) — zero outbound API calls.
   * - DMarket:      Three-tier lookup:
   *     1. Redis `dm-live:{id}` cache (5-min TTL) — instant, no API call.
   *     2. Live DMarket exchange call — ONLY for skins where the DB aggregator price is
   *        null or would be filtered by the 5× sanity check (inflated whale-listing price).
   *        These are precisely the skins where DMarket might be the real cheapest market
   *        but the aggregator hides that fact. Concurrency capped at 5 to stay within
   *        DMarket rate limits; results are cached into dm-live for 5 min.
   *     3. DB aggregator value with 5× sanity filter — for skins with a reasonable DB
   *        DMarket price (not inflated), used as-is.
   * - CS:GO Market: DB value (reliable, refreshed by bulk job every 2 h).
   *
   * Android calls this once after each list load so cards display correct prices on first render.
   */
  app.get('/prices/batch', async (request, reply) => {
    const { ids } = request.query as { ids?: string }
    if (!ids) return reply.status(400).send({ error: 'ids query param required' })

    const idList = ids.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 50)
    if (idList.length === 0) return {}

    const skins = await prisma.skin.findMany({
      where: { id: { in: idList } },
      include: { price: true },
    })

    // ── Phase 1: gather Skinport (in-memory), dm-live cache, and CS:GO Market in parallel ──
    type SkinPhase1 = {
      skin: typeof skins[number]
      skinportPrice: number | null
      csgoMarketPrice: number | null
      dmLiveCached: number | null   // null = cache miss or Redis error
      dbDmarketPrice: number | null
    }

    const phase1 = await Promise.allSettled(
      skins.map(async (skin): Promise<SkinPhase1> => {
        const [skinportPrice, dmLiveCached] = await Promise.all([
          fetchSkinportLivePrice(skin.marketHashName, 'USD')
            .then(p => p ?? skin.price?.skinportPrice ?? null),
          redis.get<number>(`dm-live:${skin.id}`).catch(() => null),
        ])
        return {
          skin,
          skinportPrice,
          csgoMarketPrice: skin.price?.csgoMarketPrice ?? null,
          dmLiveCached,
          dbDmarketPrice: skin.price?.dmarketPrice ?? null,
        }
      })
    )

    const phase1Data: SkinPhase1[] = phase1
      .filter(r => r.status === 'fulfilled')
      .map(r => (r as PromiseFulfilledResult<SkinPhase1>).value)

    // ── Phase 2: live DMarket fetch for skins where aggregator price is inflated/null ──
    // The DMarket price-aggregator (used by the bulk job) sometimes returns wildly inflated
    // values when only whale listings exist. In those cases the 5× filter nulls out DMarket,
    // so lowestPrice = min(Skinport, CS:GO Market) even though DMarket exchange might be
    // cheaper. We fix this by calling the exchange directly — but ONLY for problematic skins
    // to keep concurrency low. Cap at 5 simultaneous calls to avoid rate limits.
    const needsLiveDmarket = phase1Data.filter(({ dmLiveCached, dbDmarketPrice, skinportPrice, csgoMarketPrice }) => {
      if (dmLiveCached !== null) return false  // fresh cache hit — no call needed
      const otherPrices = [skinportPrice, csgoMarketPrice].filter((p): p is number => p != null && p > 0)
      const otherMin = otherPrices.length > 0 ? Math.min(...otherPrices) : null
      const isInflated = dbDmarketPrice !== null && otherMin !== null && dbDmarketPrice > otherMin * 5
      const isNull = dbDmarketPrice === null
      return isInflated || isNull
    })

    const liveDmarketMap = new Map<string, number>()
    const CONCURRENCY = 5
    for (let i = 0; i < needsLiveDmarket.length; i += CONCURRENCY) {
      const batch = needsLiveDmarket.slice(i, i + CONCURRENCY)
      const results = await Promise.allSettled(
        batch.map(({ skin }) =>
          fetchDMarketLivePrice(skin.marketHashName, 'USD')
            .then(price => ({ id: skin.id, price }))
        )
      )
      for (const r of results) {
        if (r.status === 'fulfilled' && r.value.price !== null) {
          liveDmarketMap.set(r.value.id, r.value.price)
          // Cache so subsequent batch calls and detail-page visits are instant
          redis.set(`dm-live:${r.value.id}`, r.value.price, { ex: CACHE_TTL.SKIN_PRICES }).catch(() => {})
        }
      }
    }

    // ── Phase 3: assemble response ─────────────────────────────────────────────
    const response: Record<string, object> = {}
    for (const { skin, skinportPrice, csgoMarketPrice, dmLiveCached, dbDmarketPrice } of phase1Data) {
      let dmarketPrice: number | null
      if (liveDmarketMap.has(skin.id)) {
        // Fresh live exchange price (most accurate)
        dmarketPrice = liveDmarketMap.get(skin.id)!
      } else if (dmLiveCached !== null) {
        // Warm Redis cache (populated by top-movers enrichment or a prior detail visit)
        dmarketPrice = dmLiveCached
      } else {
        // DB aggregator — apply 5× sanity filter to discard whale-inflated outliers
        const otherPrices = [skinportPrice, csgoMarketPrice].filter((p): p is number => p != null && p > 0)
        const otherMin = otherPrices.length > 0 ? Math.min(...otherPrices) : null
        dmarketPrice = (dbDmarketPrice !== null && otherMin !== null && dbDmarketPrice > otherMin * 5)
          ? null
          : dbDmarketPrice
      }

      const positives = [skinportPrice, dmarketPrice, csgoMarketPrice].filter(
        (p): p is number => p != null && p > 0,
      )
      const lowestPrice = positives.length > 0 ? Math.min(...positives) : null
      response[skin.id] = { skinportPrice, dmarketPrice, csgoMarketPrice, lowestPrice }
    }

    return response
  })

  // Fetch (or refresh) live prices for a skin — cached for 5 min
  app.get('/prices/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    const prices = await priceService.getPricesForSkin(skinId, skin.marketHashName)
    return prices
  })

  // Force-refresh prices for a skin (busts cache)
  app.post('/prices/:skinId/refresh', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    // Delegate to service — it will re-fetch and re-cache
    const prices = await priceService.getPricesForSkin(skinId, skin.marketHashName)
    return prices
  })
}
