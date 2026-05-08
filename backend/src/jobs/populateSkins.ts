import axios from 'axios'
import { prisma } from '../db/prisma'
import type { FastifyBaseLogger } from 'fastify'

const STEAM_MARKET_SEARCH =
  'https://steamcommunity.com/market/search/render/'

// Item types to include (skip stickers, cases, graffiti, agents, patches...)
const ALLOWED_TYPES = [
  'Rifle', 'Sniper Rifle', 'Pistol', 'SMG', 'Shotgun',
  'Machine Gun', 'Knife', 'Gloves', 'Covert', 'Classified',
]

interface SteamMarketItem {
  name: string
  hash_name: string
  sell_listings: number
  sell_price: number         // cents
  sell_price_text: string
  asset_description: {
    icon_url: string
    type: string             // e.g. "Classified Rifle"
    classid: string
  }
}

interface SteamMarketResponse {
  success: boolean
  total_count: number
  results: SteamMarketItem[]
}

function slugify(str: string): string {
  return str
    .toLowerCase()
    .replace(/[★✦]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .substring(0, 80)
}

function parseWeapon(hashName: string): string {
  const clean = hashName.replace(/^(StatTrak™|Souvenir)\s+/, '').replace(/^★\s*/, '')
  return clean.includes('|') ? clean.split('|')[0].trim() : clean.split('(')[0].trim()
}

function parseRarity(type: string): string {
  const lower = type.toLowerCase()
  if (lower.includes('consumer'))   return 'Consumer Grade'
  if (lower.includes('industrial')) return 'Industrial Grade'
  if (lower.includes('mil-spec'))   return 'Mil-Spec Grade'
  if (lower.includes('restricted')) return 'Restricted'
  if (lower.includes('classified')) return 'Classified'
  if (lower.includes('covert'))     return 'Covert'
  if (lower.includes('contraband')) return 'Contraband'
  if (lower.includes('knife') || lower.includes('gloves')) return 'Covert'
  return 'Mil-Spec Grade'
}

function isWeaponSkin(item: SteamMarketItem): boolean {
  // Must have a skin name (contains |) or be a knife/gloves
  if (!item.hash_name.includes('|') && !item.asset_description.type.toLowerCase().includes('knife') && !item.asset_description.type.toLowerCase().includes('gloves')) {
    return false
  }
  // Skip cases, capsules, stickers, graffiti, agents, patches, music kits
  const skip = ['case', 'capsule', 'sticker', 'graffiti', 'agent', 'patch', 'music kit', 'pin', 'charm', 'collectible']
  const nameLower = item.hash_name.toLowerCase()
  const typeLower = item.asset_description.type.toLowerCase()
  if (skip.some((s) => nameLower.includes(s) || typeLower.includes(s))) return false
  return true
}

async function fetchPage(start: number, count = 100): Promise<SteamMarketItem[]> {
  try {
    const { data } = await axios.get<SteamMarketResponse>(STEAM_MARKET_SEARCH, {
      params: {
        search_descriptions: 0,
        sort_column: 'popular',
        sort_dir: 'desc',
        appid: 730,
        count,
        start,
        norender: 1,
      },
      timeout: 15000,
      headers: { 'Accept-Language': 'en-US,en;q=0.9' },
    })
    return data.results ?? []
  } catch {
    return []
  }
}

export async function populateSkins(log: FastifyBaseLogger, pages = 3): Promise<void> {
  const existing = await prisma.skin.count()
  if (existing >= 50) {
    log.info(`[PopulateSkins] DB already has ${existing} skins, skipping`)
    return
  }

  log.info(`[PopulateSkins] Fetching popular CS2 skins from Steam Market...`)

  let imported = 0
  let skipped = 0

  for (let page = 0; page < pages; page++) {
    const start = page * 100
    log.info(`[PopulateSkins] Fetching page ${page + 1}/${pages} (offset ${start})`)

    const items = await fetchPage(start)
    if (items.length === 0) break

    for (const item of items) {
      if (!isWeaponSkin(item)) { skipped++; continue }

      const id = slugify(item.hash_name)
      const weapon = parseWeapon(item.hash_name)
      const rarity = parseRarity(item.asset_description.type)
      const iconUrl = `https://steamcommunity-a.akamaihd.net/economy/image/${item.asset_description.icon_url}`
      const currentPrice = item.sell_price > 0 ? item.sell_price / 100 : null

      try {
        await prisma.skin.upsert({
          where: { id },
          update: { iconUrl },
          create: {
            id,
            name: item.hash_name,
            marketHashName: item.hash_name,
            weapon,
            rarity,
            iconUrl,
          },
        })

        if (currentPrice !== null) {
          await prisma.skinPrice.upsert({
            where: { skinId: id },
            update: { steamPrice: currentPrice, lowestPrice: currentPrice, volume24h: item.sell_listings, updatedAt: new Date() },
            create: { skinId: id, steamPrice: currentPrice, lowestPrice: currentPrice, volume24h: item.sell_listings },
          })
        }

        imported++
      } catch {
        skipped++
      }
    }

    // Respect Steam rate limits between pages
    if (page < pages - 1) await new Promise((r) => setTimeout(r, 2000))
  }

  log.info(`[PopulateSkins] Done — imported: ${imported}, skipped: ${skipped}`)
}
