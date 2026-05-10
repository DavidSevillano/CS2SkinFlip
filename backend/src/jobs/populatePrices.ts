import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import type { FastifyBaseLogger } from 'fastify'

// ─── Bulk price fetchers ──────────────────────────────────────────────────────
// Strategy: each marketplace exposes a single bulk endpoint that returns the
// entire CS2 catalog in one HTTP call. We fetch all of them in parallel every
// 2h. With 12 calls/day per marketplace, rate limits are a non-issue.
//
// All endpoints below are PUBLIC and require NO authentication:
//   • Skinport      — api.skinport.com/v1/items
//   • CS:GO Market  — market.csgo.com/api/v2/prices/USD.json
//   • CSDeals       — cs.deals/API/IPricing/GetLowestPrices/v1
//   • DMarket       — api.dmarket.com/price-aggregator (paginated, 10k/page)

// ─── Skinport ────────────────────────────────────────────────────────────────

interface SkinportItem {
  market_hash_name: string
  min_price: number | null  // USD, lowest current listing
  suggested_price: number | null
}

async function fetchSkinportPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    const { data } = await axios.get<SkinportItem[]>(
      'https://api.skinport.com/v1/items',
      {
        params: { app_id: 730, currency: 'USD', tradable: 0 },
        timeout: 30000,
        headers: { 'Accept-Encoding': 'br' },
      },
    )
    for (const item of data ?? []) {
      if (typeof item.min_price === 'number' && item.min_price > 0 && item.market_hash_name) {
        map.set(item.market_hash_name, item.min_price)
      }
    }
    log.info(`[Prices] Skinport: ${map.size} items`)
  } catch (err) {
    log.warn(`[Prices] Skinport failed: ${(err as Error).message}`)
  }
  return map
}

// ─── CS:GO Market ────────────────────────────────────────────────────────────

interface CsgoMarketItem {
  market_hash_name: string
  price: string   // USD as string e.g. "32.393"
  volume: string
}

async function fetchCsgoMarketPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    const { data } = await axios.get<CsgoMarketItem[] | { items: CsgoMarketItem[] }>(
      'https://market.csgo.com/api/v2/prices/USD.json',
      { timeout: 30000, headers: { 'User-Agent': 'Mozilla/5.0' } },
    )
    const items = Array.isArray(data) ? data : (data?.items ?? [])
    for (const item of items) {
      const price = parseFloat(item.price)
      if (price > 0 && item.market_hash_name) {
        map.set(item.market_hash_name, price)
      }
    }
    log.info(`[Prices] CS:GO Market: ${map.size} items`)
  } catch (err) {
    log.warn(`[Prices] CS:GO Market failed: ${(err as Error).message}`)
  }
  return map
}

// ─── CSDeals ─────────────────────────────────────────────────────────────────

interface CsdealsResponse {
  success: boolean
  response: {
    items: Array<{ marketname: string; lowest_price: string }>
  }
}

async function fetchCsdealsPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    const { data } = await axios.get<CsdealsResponse>(
      'https://cs.deals/API/IPricing/GetLowestPrices/v1',
      {
        params: { appid: 730 },
        timeout: 30000,
        headers: { 'User-Agent': 'Mozilla/5.0' },
      },
    )
    if (!data?.success || !data.response?.items) {
      log.warn('[Prices] CSDeals: response not in expected format')
      return map
    }
    for (const item of data.response.items) {
      const price = parseFloat(item.lowest_price)
      if (price > 0 && item.marketname) {
        map.set(item.marketname, price)
      }
    }
    log.info(`[Prices] CSDeals: ${map.size} items`)
  } catch (err) {
    log.warn(`[Prices] CSDeals failed: ${(err as Error).message}`)
  }
  return map
}

// ─── DMarket aggregator ──────────────────────────────────────────────────────
// Paginated endpoint, 10k items per page, no auth. Use capital-Offset.
// Filter for CS2 (GameID 'a8db'). BestPrice is USD as a string.
// Known issue: returns inflated prices when only whale-priced listings exist —
// we apply a 5× sanity filter against the other markets in the merge step.

interface DmarketAggregatedTitle {
  Title: string
  GameID: string
  Markets?: {
    dmarket?: { Offers?: { BestPrice?: string } }
  }
}

interface DmarketResponse {
  AggregatedTitles?: DmarketAggregatedTitle[]
  Total?: { Offers?: number }
}

async function fetchDmarketPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  const PAGE_SIZE = 10000
  const MAX_PAGES = 5  // 50k items is more than enough for CS2
  try {
    for (let page = 0; page < MAX_PAGES; page++) {
      const { data } = await axios.get<DmarketResponse>(
        'https://api.dmarket.com/price-aggregator/v1/aggregated-prices',
        {
          params: { Limit: PAGE_SIZE, Offset: page * PAGE_SIZE },
          timeout: 30000,
        },
      )
      const titles = data?.AggregatedTitles ?? []
      if (titles.length === 0) break
      for (const t of titles) {
        if (t.GameID !== 'a8db') continue
        const best = t.Markets?.dmarket?.Offers?.BestPrice
        if (!best) continue
        const price = parseFloat(best)
        if (price > 0 && t.Title) map.set(t.Title, price)
      }
      if (titles.length < PAGE_SIZE) break
    }
    log.info(`[Prices] DMarket: ${map.size} items`)
  } catch (err) {
    log.warn(`[Prices] DMarket failed: ${(err as Error).message}`)
  }
  return map
}

// ─── Merge & persist ─────────────────────────────────────────────────────────

function calcLowestPrice(...prices: (number | null | undefined)[]): number | null {
  const valid = prices.filter((p): p is number => p != null && p > 0)
  return valid.length > 0 ? Math.min(...valid) : null
}

/**
 * Sanity-filter DMarket against the other markets — the aggregator occasionally
 * returns inflated whale prices (e.g. $10 000 for a $1 skin). Drop the DMarket
 * value when it exceeds 5× the cheapest of the other three.
 */
function sanityCheckDmarket(
  dmarket: number | null,
  others: (number | null)[],
): number | null {
  if (dmarket === null) return null
  const cheapest = calcLowestPrice(...others)
  if (cheapest === null) return dmarket  // no reference → trust it
  return dmarket > cheapest * 5 ? null : dmarket
}

export async function populatePrices(log: FastifyBaseLogger): Promise<void> {
  log.info('[PricePopulate] Fetching prices from 4 marketplaces in parallel...')

  const [skinportMap, csgoMarketMap, csdealsMap, dmarketMap] = await Promise.all([
    fetchSkinportPrices(log),
    fetchCsgoMarketPrices(log),
    fetchCsdealsPrices(log),
    fetchDmarketPrices(log),
  ])

  const skins = await prisma.skin.findMany({ select: { id: true, marketHashName: true } })
  log.info(`[PricePopulate] Merging prices for ${skins.length} skins...`)

  // Batch-load previous price entries for 24h change calculation
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const recentHistory = await prisma.priceHistory.findMany({
    where: { timestamp: { lte: dayAgo } },
    orderBy: { timestamp: 'desc' },
    distinct: ['skinId'],
    select: { skinId: true, price: true },
  })
  const oldPriceMap = new Map(recentHistory.map((h) => [h.skinId, h.price]))

  let updated = 0
  const BATCH = 100

  for (let i = 0; i < skins.length; i += BATCH) {
    const batch = skins.slice(i, i + BATCH)
    await Promise.all(
      batch.map(async (skin) => {
        const skinport = skinportMap.get(skin.marketHashName) ?? null
        const csgo     = csgoMarketMap.get(skin.marketHashName) ?? null
        const csdeals  = csdealsMap.get(skin.marketHashName) ?? null
        const dmarket  = sanityCheckDmarket(
          dmarketMap.get(skin.marketHashName) ?? null,
          [skinport, csgo, csdeals],
        )

        if (!skinport && !csgo && !csdeals && !dmarket) return

        const lowestPrice = calcLowestPrice(skinport, csgo, csdeals, dmarket)
        if (!lowestPrice) return

        const oldPrice = oldPriceMap.get(skin.id) ?? null
        const priceChange24h = oldPrice && oldPrice > 0
          ? ((lowestPrice - oldPrice) / oldPrice) * 100
          : null

        await prisma.skinPrice.upsert({
          where: { skinId: skin.id },
          update: {
            skinportPrice:   skinport,
            csgoMarketPrice: csgo,
            csdealsPrice:    csdeals,
            dmarketPrice:    dmarket,
            lowestPrice,
            priceChange24h,
            updatedAt: new Date(),
          },
          create: {
            skinId: skin.id,
            skinportPrice:   skinport,
            csgoMarketPrice: csgo,
            csdealsPrice:    csdeals,
            dmarketPrice:    dmarket,
            lowestPrice,
            priceChange24h,
          },
        })
        updated++
      }),
    )
  }

  // Save price history — max once per 24h per skin to avoid DB bloat
  const recentIds = new Set(
    (await prisma.priceHistory.findMany({
      where: { timestamp: { gte: new Date(Date.now() - 24 * 60 * 60 * 1000) } },
      select: { skinId: true },
      distinct: ['skinId'],
    })).map((h) => h.skinId),
  )

  const historyRows = skins
    .filter((s) => !recentIds.has(s.id))
    .flatMap((s) => {
      const skinport = skinportMap.get(s.marketHashName) ?? null
      const csgo     = csgoMarketMap.get(s.marketHashName) ?? null
      const csdeals  = csdealsMap.get(s.marketHashName) ?? null
      const dmarket  = sanityCheckDmarket(
        dmarketMap.get(s.marketHashName) ?? null,
        [skinport, csgo, csdeals],
      )
      const lowestPrice = calcLowestPrice(skinport, csgo, csdeals, dmarket)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })

  if (historyRows.length > 0) {
    await prisma.priceHistory.createMany({ data: historyRows })
  }

  await redis.del('top-movers:20')
  log.info(`[PricePopulate] Done — ${updated} skins updated, ${historyRows.length} history entries saved`)
}
