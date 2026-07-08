import axios from 'axios'
import { env } from '../config/env'
import { redis, CACHE_TTL } from '../redis/client'
import type { SteamPlayer } from '../types'

const STEAM_API = 'https://api.steampowered.com'
const STEAM_MARKET = 'https://steamcommunity.com/market'

function parsePriceString(raw: string | undefined): number | null {
  if (!raw) return null
  const cleaned = raw.replace(/[^0-9.]/g, '')
  const num = parseFloat(cleaned)
  return isNaN(num) ? null : num
}

export class SteamService {
  private readonly apiKey: string

  constructor(apiKey = env.STEAM_API_KEY) {
    this.apiKey = apiKey
  }

  async getPlayerSummary(steamId: string): Promise<SteamPlayer | null> {
    const cacheKey = `steam:player:${steamId}`
    const cached = await redis.get<SteamPlayer>(cacheKey)
    if (cached) return cached

    const { data } = await axios.get(`${STEAM_API}/ISteamUser/GetPlayerSummaries/v0002/`, {
      params: { key: this.apiKey, steamids: steamId },
    })

    const player: SteamPlayer | undefined = data.response?.players?.[0]
    if (player) {
      await redis.set(cacheKey, player, { ex: CACHE_TTL.PLAYER_SUMMARY })
    }
    return player ?? null
  }

  async getMarketPrice(
    marketHashName: string,
  ): Promise<{ lowestPrice: number | null; medianPrice: number | null; volume: number | null }> {
    const empty = { lowestPrice: null, medianPrice: null, volume: null }
    const headers = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': 'application/json, text/plain, */*',
      'Accept-Language': 'en-US,en;q=0.9',
      'Referer': 'https://steamcommunity.com/market/',
    }

    // 1) Try priceoverview — exact price for this item
    try {
      const { data } = await axios.get(`${STEAM_MARKET}/priceoverview/`, {
        params: { currency: 1, appid: 730, market_hash_name: marketHashName },
        timeout: 8000,
        headers,
      })
      if (data.success && data.lowest_price) {
        const price = parsePriceString(data.lowest_price)
        if (price && price > 0) {
          console.log(`[Steam] priceoverview $${price.toFixed(2)} for "${marketHashName}"`)
          return {
            lowestPrice: price,
            medianPrice: parsePriceString(data.median_price),
            volume: data.volume ? parseInt(String(data.volume).replace(/,/g, ''), 10) : null,
          }
        }
      }
    } catch (err: any) {
      console.warn(`[Steam] priceoverview failed (${err?.response?.status ?? err?.message}), trying search...`)
    }

    // 2) Fallback: search and match by exact hash_name
    try {
      const { data } = await axios.get(`${STEAM_MARKET}/search/render/`, {
        params: { appid: 730, q: marketHashName, count: 10, norender: 1 },
        timeout: 10000,
        headers,
      })
      const results: any[] = data.results ?? []
      const match = results.find((r) => r.hash_name === marketHashName)
      if (match && match.sell_price > 0) {
        const price = match.sell_price / 100
        console.log(`[Steam] search $${price.toFixed(2)} for "${marketHashName}"`)
        return { lowestPrice: price, medianPrice: null, volume: match.sell_listings ?? null }
      }
      console.warn(`[Steam] no exact match in search for "${marketHashName}"`)
    } catch (err: any) {
      console.error(`[Steam] search also failed for "${marketHashName}": ${err?.response?.status ?? err?.message}`)
    }

    return empty
  }
}
