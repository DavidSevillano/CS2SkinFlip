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
   * Top movers: skins with the highest (or lowest) 24h price growth, filtered
   * for signal quality.
   *
   * Pure DB read — no external API calls. The bulk job keeps both `lowestPrice`
   * and `priceChange24h` fresh every 2h, and `priceChange24h` is indexed for
   * fast sorting. We then refine the values from the more accurate `PriceHistory`
   * table to handle prices that moved between bulk runs, and drop rows that
   * don't clear a minimum price ($1) and minimum absolute 24h change ($0.25) —
   * below those thresholds the "movement" is almost always marketplace spread
   * noise rather than a real, flippable price move.
   */
  async getTopMovers(
    direction: 'rising' | 'falling' = 'rising',
    limit = 20
  ): Promise<Array<AggregatedPrices & { name: string; iconUrl: string }>> {
    const cacheKey = `top-movers:${direction}:${limit}`
    const cached = await redis.get<Array<AggregatedPrices & { name: string; iconUrl: string }>>(cacheKey)
    if (cached) return cached

    const sortOrder = direction === 'falling' ? 'asc' : 'desc'

    // Pre-sorted by DB priceChange24h (indexed). Candidate batch is wider than
    // `limit` because the quality filter below can discard rows (e.g. no
    // PriceHistory entry from ~24h ago, or the move is too small in absolute
    // terms) and we don't want to under-fill the final list.
    const skins = await prisma.skin.findMany({
      include: { price: true },
      where: { price: { lowestPrice: { gte: 1 } } },
      orderBy: [
        { price: { priceChange24h: { sort: sortOrder, nulls: 'last' } } },
        { price: { lowestPrice: 'desc' } },
      ],
      take: Math.max(limit * 5, 100),
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
            : null
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
          _prev: prev,
        }
      })
      // Quality filter: need a real 24h-ago reference price and a meaningful
      // absolute move. Without `_prev` we can't tell noise from a real move.
      .filter((s) => s._prev !== null && s.lowestPrice !== null && Math.abs(s.lowestPrice - s._prev) >= 0.25)
      .sort((a, b) => {
        const diff =
          direction === 'falling'
            ? (a.priceChange24h ?? Infinity) - (b.priceChange24h ?? Infinity)
            : (b.priceChange24h ?? -Infinity) - (a.priceChange24h ?? -Infinity)
        if (diff !== 0) return diff
        return (b.lowestPrice ?? 0) - (a.lowestPrice ?? 0)
      })
      .slice(0, limit)
      .map(({ _prev, ...rest }) => rest)

    if (topMovers.length > 0) {
      await redis.set(cacheKey, topMovers, { ex: CACHE_TTL.TOP_MOVERS })
    }
    return topMovers
  }
}
