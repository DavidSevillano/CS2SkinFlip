import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
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
      skinportPrice:   row?.skinportPrice   ?? null,
      csgoMarketPrice: row?.csgoMarketPrice ?? null,
      waxpeerPrice:    row?.waxpeerPrice    ?? null,
      lowestPrice:     row?.lowestPrice     ?? null,
      priceChange24h,
      volume24h:       row?.volume24h       ?? null,
      updatedAt:       row?.updatedAt.toISOString() ?? new Date().toISOString(),
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

  /**
   * Top movers: skins with the highest 24h price growth.
   *
   * Pure DB read — no external API calls. The bulk job keeps both `lowestPrice`
   * and `priceChange24h` fresh every 2h, and `priceChange24h` is indexed for
   * fast sorting. We then refine the values from the more accurate `PriceHistory`
   * table to handle prices that moved between bulk runs.
   */
  async getTopMovers(limit = 20): Promise<Array<AggregatedPrices & { name: string; iconUrl: string }>> {
    const cacheKey = `top-movers:${limit}`
    const cached = await redis.get<Array<AggregatedPrices & { name: string; iconUrl: string }>>(cacheKey)
    if (cached) return cached

    // Pre-sorted by DB priceChange24h (indexed), falling back to price when
    // change data is null (e.g. before 24h of price history has accumulated) —
    // otherwise ties all sort to Postgres's arbitrary insertion order.
    const skins = await prisma.skin.findMany({
      include: { price: true },
      where: { price: { lowestPrice: { gt: 0 } } },
      orderBy: [
        { price: { priceChange24h: { sort: 'desc', nulls: 'last' } } },
        { price: { lowestPrice: 'desc' } },
      ],
      take: limit * 3,
    })

    const skinIds = skins.map((s) => s.id)
    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const histories = await prisma.priceHistory.findMany({
      where: { skinId: { in: skinIds }, timestamp: { lte: dayAgo } },
      orderBy: { timestamp: 'desc' },
      distinct: ['skinId'],
    })
    const historyMap = new Map(histories.map((h) => [h.skinId, h.price]))

    const topMovers = skins
      .map((skin) => {
        const current = skin.price?.lowestPrice ?? null
        const prev = historyMap.get(skin.id) ?? null
        const priceChange24h =
          current !== null && prev !== null && prev > 0
            ? ((current - prev) / prev) * 100
            : (skin.price?.priceChange24h ?? null)
        return {
          skinId: skin.id,
          marketHashName: skin.marketHashName,
          name: skin.name,
          iconUrl: skin.iconUrl,
          skinportPrice:   skin.price?.skinportPrice   ?? null,
          csgoMarketPrice: skin.price?.csgoMarketPrice ?? null,
          waxpeerPrice:    skin.price?.waxpeerPrice    ?? null,
          lowestPrice: current,
          priceChange24h,
          volume24h: skin.price?.volume24h ?? null,
          updatedAt: skin.price?.updatedAt.toISOString() ?? new Date().toISOString(),
        }
      })
      .sort((a, b) => {
        const diff = (b.priceChange24h ?? -Infinity) - (a.priceChange24h ?? -Infinity)
        if (diff !== 0) return diff
        return (b.lowestPrice ?? 0) - (a.lowestPrice ?? 0)
      })
      .slice(0, limit)

    if (topMovers.length > 0) {
      await redis.set(cacheKey, topMovers, { ex: CACHE_TTL.TOP_MOVERS })
    }
    return topMovers
  }
}
