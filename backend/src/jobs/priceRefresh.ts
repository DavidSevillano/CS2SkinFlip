import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { SteamService } from '../services/steam'
import type { FastifyBaseLogger } from 'fastify'

const steam = new SteamService()
const REFRESH_INTERVAL_MS = 5 * 60 * 1000 // 5 minutes
const DELAY_BETWEEN_REQUESTS_MS = 3000     // Steam rate limit: ~20 req/min

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function refreshAllPrices(log: FastifyBaseLogger) {
  // Refresh top 100 skins — prioritise those with existing prices, then fill from catalog
  const withPrice = await prisma.skin.findMany({
    select: { id: true, marketHashName: true },
    where: { price: { isNot: null } },
    orderBy: { price: { volume24h: 'desc' } },
    take: 60,
  })
  const withoutPrice = await prisma.skin.findMany({
    select: { id: true, marketHashName: true },
    where: { price: null },
    take: 40,
  })
  const skins = [...withPrice, ...withoutPrice]

  log.info(`[PriceRefresh] Refreshing prices for ${skins.length} skins...`)

  let updated = 0
  let failed = 0

  for (const skin of skins) {
    try {
      const { lowestPrice, volume } = await steam.getMarketPrice(skin.marketHashName)

      if (lowestPrice !== null) {
        // Update current price
        await prisma.skinPrice.upsert({
          where: { skinId: skin.id },
          update: { steamPrice: lowestPrice, lowestPrice, volume24h: volume, updatedAt: new Date() },
          create: { skinId: skin.id, steamPrice: lowestPrice, lowestPrice, volume24h: volume },
        })

        // Save to price history
        await prisma.priceHistory.create({
          data: { skinId: skin.id, price: lowestPrice, source: 'steam' },
        })

        // Invalidate Redis cache for this skin
        await redis.del(`prices:${skin.id}`)

        updated++
      } else {
        failed++
        log.warn(`[PriceRefresh] No price for: ${skin.marketHashName}`)
      }
    } catch (err) {
      failed++
      log.error(`[PriceRefresh] Error fetching ${skin.marketHashName}: ${err}`)
    }

    // Respect Steam rate limits
    await sleep(DELAY_BETWEEN_REQUESTS_MS)
  }

  // Invalidate top-movers cache so next request returns fresh data
  await redis.del('top-movers:20')

  log.info(`[PriceRefresh] Done — updated: ${updated}, failed: ${failed}`)
}

export function startPriceRefreshJob(log: FastifyBaseLogger) {
  // First run after 5 minutes (populate job already has fresh prices)
  const interval = setInterval(() => {
    refreshAllPrices(log).catch((err) => log.error('[PriceRefresh] Scheduled run failed:', err))
  }, REFRESH_INTERVAL_MS)

  log.info(`[PriceRefresh] Job started — refreshing every ${REFRESH_INTERVAL_MS / 1000 / 60} minutes`)

  return interval
}
