import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import type { FastifyBaseLogger } from 'fastify'

// ─── Skinport ────────────────────────────────────────────────────────────────
// Free public bulk API — returns all CS2 items with prices in one call

interface SkinportItem {
  market_hash_name: string
  min_price: number | null
  quantity: number | null
}

async function fetchSkinportPrices(log: FastifyBaseLogger): Promise<Map<string, { price: number; volume: number }>> {
  const map = new Map<string, { price: number; volume: number }>()
  try {
    const { data } = await axios.get<SkinportItem[]>('https://api.skinport.com/v1/items', {
      params: { app_id: 730, currency: 'USD' },
      timeout: 60000,
      headers: { 'Accept': 'application/json', 'Accept-Encoding': 'gzip, deflate, br' },
    })
    for (const item of data) {
      if (item.min_price && item.min_price > 0) {
        map.set(item.market_hash_name, { price: item.min_price, volume: item.quantity ?? 0 })
      }
    }
    log.info(`[Prices] Skinport: ${map.size} items with prices`)
  } catch (err) {
    log.error(`[Prices] Skinport failed: ${err}`)
  }
  return map
}

// ─── CS:GO Market ────────────────────────────────────────────────────────────
// market.csgo.com free public endpoint — 25 k+ items, market_hash_name keys,
// price already in USD as a string.

interface CsgoMarketItem {
  market_hash_name: string
  price: string   // USD e.g. "32.393"
  volume: string  // 24h volume
}

async function fetchCsgoMarketPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    const { data } = await axios.get<CsgoMarketItem[]>(
      'https://market.csgo.com/api/v2/prices/USD.json',
      { timeout: 30000, headers: { 'User-Agent': 'Mozilla/5.0' } }
    )
    const items = Array.isArray(data) ? data : (data as any).items ?? []
    for (const item of items) {
      const price = parseFloat(item.price)
      if (price > 0 && item.market_hash_name) map.set(item.market_hash_name, price)
    }
    log.info(`[Prices] CS:GO Market: ${map.size} items`)
  } catch (err) {
    log.warn(`[Prices] CS:GO Market failed: ${err}`)
  }
  return map
}

// ─── DMarket ─────────────────────────────────────────────────────────────────
// Aggregated prices endpoint — cap of 10k items per page (limit param ignored).
// Pagination: capital-O `Offset` param. Total is ~51k across all games;
// filter to GameID==='a8db' for CS2 only. 6 pages → ~25k CS2 items.
// BestPrice is already in USD dollars (not cents).

interface DMarketAggregatedItem {
  MarketHashName: string
  Offers: { BestPrice: string; Count: number }
  GameID: string
}

async function fetchDMarketPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    const PAGE_SIZE = 10000
    let offset = 0
    let total = Infinity

    while (offset < total) {
      const { data } = await axios.get<{ Total: string; AggregatedTitles: DMarketAggregatedItem[] }>(
        'https://api.dmarket.com/price-aggregator/v1/aggregated-prices',
        { params: { gameId: 'a8db', currency: 'USD', Offset: offset }, timeout: 30000 }
      )

      if (offset === 0) total = parseInt(data.Total, 10) || 0

      const items = data.AggregatedTitles ?? []
      if (items.length === 0) break

      for (const item of items) {
        if (item.GameID !== 'a8db') continue   // skip Dota2, Rust, TF2 etc.
        const name = item.MarketHashName
        if (!name) continue
        const price = parseFloat(item.Offers?.BestPrice ?? '0')
        if (price > 0) map.set(name, price)
      }

      offset += PAGE_SIZE
    }

    log.info(`[Prices] DMarket: ${map.size} CS2 items (total across all games: ${total})`)
  } catch (err) {
    log.warn(`[Prices] DMarket failed: ${err}`)
  }
  return map
}

// ─── Merge & persist ─────────────────────────────────────────────────────────

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

function calcLowestPrice(...prices: (number | null | undefined)[]): number | null {
  const valid = prices.filter((p): p is number => p != null && p > 0)
  return valid.length > 0 ? Math.min(...valid) : null
}

export async function populatePricesFromSkinport(log: FastifyBaseLogger): Promise<void> {
  log.info('[PricePopulate] Fetching prices from Skinport, DMarket and CS:GO Market...')
  const skinportMap    = await fetchSkinportPrices(log)
  const dmarketMap     = await fetchDMarketPrices(log)
  const csgoMarketMap  = await fetchCsgoMarketPrices(log)

  // Load all DB skins once
  const skins = await prisma.skin.findMany({ select: { id: true, marketHashName: true } })
  log.info(`[PricePopulate] Merging prices for ${skins.length} skins...`)

  let updated = 0
  const BATCH = 100

  for (let i = 0; i < skins.length; i += BATCH) {
    const batch = skins.slice(i, i + BATCH)
    await Promise.all(batch.map(async (skin) => {
      const sp  = skinportMap.get(skin.marketHashName)
      const dm  = dmarketMap.get(skin.marketHashName)
      const cgm = csgoMarketMap.get(skin.marketHashName)

      if (!sp && !dm && !cgm) return  // no data for this skin

      const skinportPrice   = sp?.price ?? null
      const dmarketPrice    = dm ?? null
      const csgoMarketPrice = cgm ?? null
      const volume = sp?.volume ?? 0
      const lowestPrice = calcLowestPrice(skinportPrice, dmarketPrice, csgoMarketPrice)

      if (!lowestPrice) return

      await prisma.skinPrice.upsert({
        where: { skinId: skin.id },
        update: {
          skinportPrice,
          dmarketPrice,
          csgoMarketPrice,
          lowestPrice,
          volume24h: volume,
          updatedAt: new Date(),
        },
        create: {
          skinId: skin.id,
          skinportPrice,
          dmarketPrice,
          csgoMarketPrice,
          lowestPrice,
          volume24h: volume,
        },
      })
      updated++
    }))
  }

  // Save price history — max once per 24h per skin to avoid DB bloat
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const recentIds = new Set(
    (await prisma.priceHistory.findMany({
      where: { timestamp: { gte: dayAgo } },
      select: { skinId: true },
      distinct: ['skinId'],
    })).map((h) => h.skinId)
  )

  const historyRows = skins
    .filter((s) => !recentIds.has(s.id))
    .flatMap((s) => {
      const sp  = skinportMap.get(s.marketHashName)
      const dm  = dmarketMap.get(s.marketHashName)
      const cgm = csgoMarketMap.get(s.marketHashName)
      const lowestPrice = calcLowestPrice(sp?.price, dm, cgm)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })

  if (historyRows.length > 0) {
    await prisma.priceHistory.createMany({ data: historyRows })
  }

  await redis.del('top-movers:20')
  log.info(`[PricePopulate] Done — ${updated} skins updated, ${historyRows.length} history entries saved`)
}
