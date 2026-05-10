import { FastifyPluginAsync } from 'fastify'
import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { PriceService } from '../services/prices'
import { fetchSkinportLivePrice, fetchDMarketLivePrice } from '../jobs/populatePrices'

const priceService = new PriceService()

export const priceRoutes: FastifyPluginAsync = async (app) => {
  app.get('/debug/dmarket/:name', async (request, reply) => {
    const { name } = request.params as { name: string }
    const decoded = decodeURIComponent(name)
    const dmarketPrice = await fetchDMarketLivePrice(decoded, 'USD')
    const skinportPrice = await fetchSkinportLivePrice(decoded, 'USD')
    return { dmarketPrice, skinportPrice, name: decoded }
  })
  /**
   * GET /prices/batch?ids=id1,id2,...
   *
   * Returns accurate prices for up to 50 skins in a single round-trip.
   * Always fetches live DMarket prices to ensure accurate lowest price.
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

    // Fetch live prices - parallel with concurrency limit for speed
    const CONCURRENCY = 3
    const results: Array<{ id: string; skinportPrice: number | null; dmarketPrice: number | null; csgoMarketPrice: number | null; lowestPrice: number | null }> = []

    const processSkin = async (skin: typeof skins[number]) => {
      const [skinportPrice, dmarketPrice] = await Promise.all([
        fetchSkinportLivePrice(skin.marketHashName, 'USD')
          .then(p => p ?? skin.price?.skinportPrice ?? null),
        fetchDMarketLivePrice(skin.marketHashName, 'USD')
          .then(p => p ?? skin.price?.dmarketPrice ?? null),  // fallback to DB if live fails
      ])
      
      const csgoMarketPrice = skin.price?.csgoMarketPrice ?? null

      const prices = [skinportPrice, dmarketPrice, csgoMarketPrice]
        .filter((p): p is number => p != null && p > 0)
      
      const lowestPrice = prices.length > 0 ? Math.min(...prices) : null

      return {
        id: skin.id,
        skinportPrice,
        dmarketPrice,
        csgoMarketPrice,
        lowestPrice,
        skin,
      }
    }

    // Process in batches of CONCURRENCY to avoid rate limiting
    for (let i = 0; i < skins.length; i += CONCURRENCY) {
      const batch = skins.slice(i, i + CONCURRENCY)
      // Add small delay between batches
      if (i > 0) await new Promise(r => setTimeout(r, 100))
      const batchResults = await Promise.all(batch.map(processSkin))
      results.push(...batchResults)
    }

    // Save all prices to DB in a single batch (faster than one by one)
    const updates = results
      .filter(r => r.dmarketPrice !== null)
      .map(r => prisma.skinPrice.update({
        where: { skinId: r.id },
        data: {
          skinportPrice: r.skinportPrice,
          dmarketPrice: r.dmarketPrice,
          csgoMarketPrice: r.csgoMarketPrice,
          lowestPrice: r.lowestPrice,
          updatedAt: new Date(),
        },
      }).catch(() => null))

    if (updates.length > 0) {
      await Promise.all(updates).catch(() => {})
    }

    const response: Record<string, object> = {}
    for (const result of results) {
      app.log.info(`[BATCH] ${result.id}: sp=${result.skinportPrice}, dm=${result.dmarketPrice}, csgom=${result.csgoMarketPrice}, lowest=${result.lowestPrice}`)
      response[result.id] = {
        skinportPrice: result.skinportPrice,
        dmarketPrice: result.dmarketPrice,
        csgoMarketPrice: result.csgoMarketPrice,
        lowestPrice: result.lowestPrice,
      }
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