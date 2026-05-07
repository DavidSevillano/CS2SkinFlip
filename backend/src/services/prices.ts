import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { SteamService } from './steam'
import type { AggregatedPrices } from '../types'

export class PriceService {
  private steam = new SteamService()

  async getPricesForSkin(skinId: string, marketHashName: string): Promise<AggregatedPrices> {
    const cacheKey = `prices:${skinId}`
    const cached = await redis.get<AggregatedPrices>(cacheKey)
    if (cached) return cached

    const steamData = await this.steam.getMarketPrice(marketHashName)

    // Lowest available price across all markets (only Steam implemented for now)
    const candidates = [steamData.lowestPrice].filter((p): p is number => p !== null)
    const lowestPrice = candidates.length > 0 ? Math.min(...candidates) : null

    const priceChange24h = await this.calculate24hChange(skinId, steamData.lowestPrice)

    const prices: AggregatedPrices = {
      skinId,
      marketHashName,
      steamPrice: steamData.lowestPrice,
      csFloatPrice: null,   // TODO: integrate CSFloat API
      skinportPrice: null,  // TODO: integrate Skinport API
      dmarketPrice: null,   // TODO: integrate DMarket API
      lowestPrice,
      priceChange24h,
      volume24h: steamData.volume,
      updatedAt: new Date().toISOString(),
    }

    await prisma.skinPrice.upsert({
      where: { skinId },
      update: {
        steamPrice: prices.steamPrice,
        lowestPrice: prices.lowestPrice,
        volume24h: prices.volume24h,
      },
      create: {
        skinId,
        steamPrice: prices.steamPrice,
        lowestPrice: prices.lowestPrice,
        volume24h: prices.volume24h,
      },
    })

    if (steamData.lowestPrice !== null) {
      await prisma.priceHistory.create({
        data: { skinId, price: steamData.lowestPrice, source: 'steam' },
      })
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
    const cacheKey = `top-movers:${limit}`
    const cached = await redis.get<Array<AggregatedPrices & { name: string; iconUrl: string }>>(cacheKey)
    if (cached) return cached

    const skins = await prisma.skin.findMany({
      include: { price: true },
      where: { price: { isNot: null } },
    })

    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)

    const results = await Promise.all(
      skins.map(async (skin) => {
        const current = skin.price?.lowestPrice ?? null
        const record = await prisma.priceHistory.findFirst({
          where: { skinId: skin.id, timestamp: { lte: dayAgo } },
          orderBy: { timestamp: 'desc' },
        })
        const priceChange24h =
          current !== null && record !== null
            ? ((current - record.price) / record.price) * 100
            : null

        return {
          skinId: skin.id,
          marketHashName: skin.marketHashName,
          name: skin.name,
          iconUrl: skin.iconUrl,
          steamPrice: skin.price?.steamPrice ?? null,
          csFloatPrice: skin.price?.csFloatPrice ?? null,
          skinportPrice: skin.price?.skinportPrice ?? null,
          dmarketPrice: skin.price?.dmarketPrice ?? null,
          lowestPrice: current,
          priceChange24h,
          volume24h: skin.price?.volume24h ?? null,
          updatedAt: skin.price?.updatedAt.toISOString() ?? new Date().toISOString(),
        }
      }),
    )

    const topMovers = results
      .filter((s) => s.priceChange24h !== null)
      .sort((a, b) => Math.abs(b.priceChange24h!) - Math.abs(a.priceChange24h!))
      .slice(0, limit)

    await redis.set(cacheKey, topMovers, { ex: CACHE_TTL.TOP_MOVERS })
    return topMovers
  }
}
