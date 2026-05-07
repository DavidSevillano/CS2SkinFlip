import axios from 'axios'
import { env } from '../config/env'
import { redis, CACHE_TTL } from '../redis/client'
import type { SteamPlayer, InventoryAsset, InventoryDescription, InventoryItem } from '../types'

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

  async getInventory(steamId: string): Promise<InventoryItem[]> {
    const cacheKey = `steam:inventory:${steamId}`
    const cached = await redis.get<InventoryItem[]>(cacheKey)
    if (cached) return cached

    const { data } = await axios.get(`${STEAM_API}/IEconService/GetInventoryItemsWithDescriptions/v1/`, {
      params: {
        key: this.apiKey,
        steamid: steamId,
        appid: 730,
        contextid: 2,
        get_descriptions: true,
      },
    })

    const assets: InventoryAsset[] = data.assets ?? []
    const descriptions: InventoryDescription[] = data.descriptions ?? []

    const descMap = new Map(descriptions.map((d) => [`${d.classid}_${d.instanceid}`, d]))

    const items: InventoryItem[] = assets
      .map((asset) => {
        const desc = descMap.get(`${asset.classid}_${asset.instanceid}`)
        return {
          assetId: asset.assetid,
          marketHashName: desc?.market_hash_name ?? '',
          name: desc?.name ?? '',
          iconUrl: desc
            ? `https://steamcommunity-a.akamaihd.net/economy/image/${desc.icon_url}`
            : '',
          amount: parseInt(asset.amount, 10),
          tags: desc?.tags ?? [],
        }
      })
      .filter((item) => item.marketHashName)

    await redis.set(cacheKey, items, { ex: CACHE_TTL.INVENTORY })
    return items
  }

  async getMarketPrice(
    marketHashName: string,
  ): Promise<{ lowestPrice: number | null; medianPrice: number | null; volume: number | null }> {
    try {
      const { data } = await axios.get(`${STEAM_MARKET}/priceoverview/`, {
        params: { currency: 1, appid: 730, market_hash_name: marketHashName },
        timeout: 5000,
      })
      return {
        lowestPrice: parsePriceString(data.lowest_price),
        medianPrice: parsePriceString(data.median_price),
        volume: data.volume ? parseInt(String(data.volume).replace(/,/g, ''), 10) : null,
      }
    } catch {
      return { lowestPrice: null, medianPrice: null, volume: null }
    }
  }
}
