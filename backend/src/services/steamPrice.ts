import axios from 'axios'
import { redis, CACHE_TTL } from '../redis/client'
import type { FastifyBaseLogger } from 'fastify'

// Steam Community Market has no bulk catalog endpoint — priceoverview returns
// one item per call and is aggressively rate-limited per IP. Unlike the other
// three marketplaces (fetched in bulk every 2h in populatePrices.ts), this is
// fetched live, on demand, only for the skin currently open in the detail
// screen, and cached for CACHE_TTL.STEAM_PRICE (1h) — including a "not listed"
// result — so a popular skin generates at most one real Steam call per hour
// across every user viewing it, not one call per view.

interface SteamPriceOverviewResponse {
  success: boolean
  lowest_price?: string   // e.g. "$32.39" or "$1,234.56" (currency=1 requested → USD)
  median_price?: string
  volume?: string
}

function parseSteamPrice(raw: string | undefined): number | null {
  if (!raw) return null
  const cleaned = raw.replace(/[^0-9.]/g, '')
  const value = parseFloat(cleaned)
  return Number.isFinite(value) && value > 0 ? value : null
}

export async function getSteamPrice(
  skinId: string,
  marketHashName: string,
  log: FastifyBaseLogger,
): Promise<number | null> {
  const cacheKey = `steam-price:${skinId}`
  const cached = await redis.get<{ price: number | null }>(cacheKey)
  if (cached) return cached.price

  let price: number | null = null
  try {
    const { data } = await axios.get<SteamPriceOverviewResponse>(
      'https://steamcommunity.com/market/priceoverview/',
      {
        params: { appid: 730, currency: 1, market_hash_name: marketHashName },
        timeout: 8000,
        headers: { 'User-Agent': 'Mozilla/5.0' },
      },
    )
    if (data?.success) {
      price = parseSteamPrice(data.lowest_price)
    }
  } catch (err) {
    log.warn(`[SteamPrice] fetch failed for ${marketHashName}: ${(err as Error).message}`)
  }

  await redis.set(cacheKey, { price }, { ex: CACHE_TTL.STEAM_PRICE })
  return price
}
