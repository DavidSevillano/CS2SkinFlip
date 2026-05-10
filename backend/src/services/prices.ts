import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { fetchDMarketLivePrice } from '../jobs/populatePrices'
import type { AggregatedPrices } from '../types'

export class PriceService {

  /** Read stored prices from DB (populated by bulk job every 2h). Cached 5 min. */
  async getPricesForSkin(skinId: string, marketHashName: string): Promise<AggregatedPrices> {
    const cacheKey = `prices:${skinId}`
    const cached = await redis.get<AggregatedPrices>(cacheKey)
    if (cached) return cached

    const row = await prisma.skinPrice.findUnique({ where: { skinId } })
    const priceChange24h = await this.calculate24hChange(skinId, row?.lowestPrice ?? null)

    const prices: AggregatedPrices = {
      skinId,
      marketHashName,
      skinportPrice: row?.skinportPrice ?? null,
      dmarketPrice: row?.dmarketPrice ?? null,
      csgoMarketPrice: row?.csgoMarketPrice ?? null,
      lowestPrice: row?.lowestPrice ?? null,
      priceChange24h,
      volume24h: row?.volume24h ?? null,
      updatedAt: row?.updatedAt.toISOString() ?? new Date().toISOString(),
    }

    await redis.set(cacheKey, prices, { ex: CACHE_TTL.SKIN_PRICES })
    return prices
  }

  async calculate24hChange(skinId: string, currentPrice: number | null): Promise<number | null> {
    if (currentPrice === null) return null

    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const record = await prisma.priceHistory.findFirst({
      where: { skinId, timestamp: { lte: dayAgo } },
      orderBy: { timestamp: 'desc' },
    })

    if (!record) return null
    return ((currentPrice - record.price) / record.price) * 100
  }

  async getTopMovers(limit = 20): Promise<Array<AggregatedPrices & { name: string; iconUrl: string }>> {
    // Always fetch fresh prices - no cache to ensure correct prices on first load
    // Get all skins with prices, then calculate 24h change and sort by biggest gainers
    const skins = await prisma.skin.findMany({
      include: { price: true },
      where: { price: { lowestPrice: { gt: 0 } } },
    })

    // Get price history from 24h ago for all skins
    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const histories = await prisma.priceHistory.findMany({
      where: { timestamp: { lte: dayAgo } },
      orderBy: { timestamp: 'desc' },
      distinct: ['skinId'],
    })
    const historyMap = new Map(histories.map((h) => [h.skinId, h.price]))

    // Calculate price change and sort by biggest gainers
    const skinsWithChange = skins.map((skin) => {
      const current = skin.price?.lowestPrice ?? null
      const prev = historyMap.get(skin.id) ?? null
      const priceChange24h = current !== null && prev !== null && prev > 0
        ? ((current - prev) / prev) * 100
        : null
      return { skin, priceChange24h }
    })

    // Sort by biggest price increase (or by price if no change data)
    const sorted = skinsWithChange.sort((a, b) => {
      if (a.priceChange24h !== null && b.priceChange24h !== null) {
        return b.priceChange24h - a.priceChange24h // biggest gainers first
      }
      if (a.priceChange24h !== null) return -1
      if (b.priceChange24h !== null) return 1
      return (b.skin.price?.lowestPrice ?? 0) - (a.skin.price?.lowestPrice ?? 0)
    })

    const topMovers = sorted.slice(0, limit).map(({ skin, priceChange24h }) => ({
      skinId: skin.id,
      marketHashName: skin.marketHashName,
      name: skin.name,
      iconUrl: skin.iconUrl,
      skinportPrice: skin.price?.skinportPrice ?? null,
      dmarketPrice: skin.price?.dmarketPrice ?? null,
      csgoMarketPrice: skin.price?.csgoMarketPrice ?? null,
      lowestPrice: skin.price?.lowestPrice ?? null,
      priceChange24h,
      volume24h: skin.price?.volume24h ?? null,
      updatedAt: skin.price?.updatedAt.toISOString() ?? new Date().toISOString(),
    }))

    // Enrich top-movers with live DMarket exchange prices before caching.
    // This runs at most every 15 min (when the Redis cache expires), never per user request,
    // so 20 parallel DMarket API calls here are well within rate limits.
    // Results are also written to dm-live:{skinId} (5-min TTL) so the batch endpoint
    // can serve correct DMarket prices without making any additional API calls.
    const enriched = await Promise.all(
      topMovers.map(async (mover) => {
        try {
          const live = await fetchDMarketLivePrice(mover.marketHashName, 'USD')
          if (live === null) return mover
          // Populate dm-live cache so /prices/batch picks it up for search results too
          redis.set(`dm-live:${mover.skinId}`, live, { ex: CACHE_TTL.SKIN_PRICES }).catch(() => {})
          const positives = [mover.skinportPrice, live, mover.csgoMarketPrice]
            .filter((p): p is number => p != null && p > 0)
          const lowestPrice = positives.length > 0 ? Math.min(...positives) : mover.lowestPrice
          return { ...mover, dmarketPrice: live, lowestPrice }
        } catch {
          return mover
        }
      }),
    )

    return enriched
  }
}
