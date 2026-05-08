import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import type { FastifyBaseLogger } from 'fastify'

const STEAM_MARKET_SEARCH = 'https://steamcommunity.com/market/search/render/'

interface SteamMarketItem {
  hash_name: string
  sell_price: number   // cents
  sell_listings: number
}

interface SteamSearchResponse {
  success: boolean
  total_count: number
  results: SteamMarketItem[]
}

async function fetchPopularPage(start: number, count = 100): Promise<SteamMarketItem[]> {
  try {
    const { data } = await axios.get<SteamSearchResponse>(STEAM_MARKET_SEARCH, {
      params: { appid: 730, sort_column: 'popular', sort_dir: 'desc', count, start, norender: 1 },
      timeout: 15000,
      headers: { 'Accept-Language': 'en-US,en;q=0.9' },
    })
    return data.results ?? []
  } catch {
    return []
  }
}

export async function populatePricesFromSteam(log: FastifyBaseLogger, pages = 5): Promise<void> {
  log.info('[PricePopulate] Fetching prices from Steam Market search...')

  let updated = 0

  for (let page = 0; page < pages; page++) {
    const items = await fetchPopularPage(page * 100)
    if (items.length === 0) break

    for (const item of items) {
      if (item.sell_price <= 0) continue

      const price = item.sell_price / 100

      // Find skin in DB by marketHashName
      const skin = await prisma.skin.findFirst({
        where: { marketHashName: item.hash_name },
        select: { id: true },
      })

      if (!skin) continue

      await prisma.skinPrice.upsert({
        where: { skinId: skin.id },
        update: {
          steamPrice: price,
          lowestPrice: price,
          volume24h: item.sell_listings,
          updatedAt: new Date(),
        },
        create: {
          skinId: skin.id,
          steamPrice: price,
          lowestPrice: price,
          volume24h: item.sell_listings,
        },
      })

      updated++
    }

    log.info(`[PricePopulate] Page ${page + 1}/${pages} — updated: ${updated} so far`)

    // Small delay between pages
    if (page < pages - 1) await new Promise((r) => setTimeout(r, 2000))
  }

  await redis.del('top-movers:20')
  log.info(`[PricePopulate] Done — ${updated} skins now have prices`)
}
