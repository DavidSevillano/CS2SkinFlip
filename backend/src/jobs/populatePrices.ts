import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import type { FastifyBaseLogger } from 'fastify'

// ─── Skinport ────────────────────────────────────────────────────────────────
// Free public bulk API — returns all ~20k CS2 items in one call.
// We keep the full price map in process memory (30 min TTL) for fast per-skin
// live lookups — Redis is used as a secondary backup but the in-memory map is
// the primary source so key-encoding or size issues never cause stale fallbacks.

const SKINPORT_MAP_KEY_PREFIX = 'skinport:price-map'
const SKINPORT_MEM_TTL_MS = CACHE_TTL.SKINPORT_MAP * 1000  // 30 min in ms

// Skinport supports these natively. Anything else falls back to USD.
const SKINPORT_SUPPORTED_CURRENCIES = ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY', 'CHF', 'PLN'] as const
type SkinportCurrency = typeof SKINPORT_SUPPORTED_CURRENCIES[number]

function normalizeSkinportCurrency(c: string): SkinportCurrency {
  const up = c.toUpperCase()
  return (SKINPORT_SUPPORTED_CURRENCIES as readonly string[]).includes(up)
    ? (up as SkinportCurrency)
    : 'USD'
}

// One in-memory map per currency — Skinport prices natively in EUR and converts internally,
// so converting USD→EUR with a static fx rate gives the wrong number.
const _skinportMaps: Map<string, { map: Map<string, { price: number; volume: number }>; fetchedAt: number }> =
  new Map()

interface SkinportItem {
  market_hash_name: string
  min_price: number | null
  quantity: number | null
}

async function fetchSkinportPrices(
  log?: FastifyBaseLogger,
  currency: SkinportCurrency = 'USD',
): Promise<Map<string, { price: number; volume: number }>> {
  const map = new Map<string, { price: number; volume: number }>()
  try {
    const { data } = await axios.get<SkinportItem[]>('https://api.skinport.com/v1/items', {
      params: { app_id: 730, currency },
      timeout: 60000,
      headers: { 'Accept': 'application/json', 'Accept-Encoding': 'gzip, deflate, br' },
    })
    for (const item of data) {
      if (item.min_price && item.min_price > 0) {
        map.set(item.market_hash_name, { price: item.min_price, volume: item.quantity ?? 0 })
      }
    }

    _skinportMaps.set(currency, { map, fetchedAt: Date.now() })

    // Redis backup — only USD (used by the bulk job that populates DB prices)
    if (currency === 'USD') {
      try {
        const obj: Record<string, { price: number; volume: number }> = {}
        map.forEach((v, k) => { obj[k] = v })
        await redis.set(`${SKINPORT_MAP_KEY_PREFIX}:USD`, obj, { ex: CACHE_TTL.SKINPORT_MAP })
      } catch { /* Redis backup optional */ }
    }

    log?.info(`[Prices] Skinport (${currency}): ${map.size} items with prices`)
  } catch (err) {
    log?.error(`[Prices] Skinport (${currency}) failed: ${err}`)
  }
  return map
}

/**
 * Live per-skin Skinport price in the requested currency. Uses an in-memory map per
 * currency so we serve Skinport's native EUR/GBP/etc. price (their internal fx ≠ ours).
 */
export async function fetchSkinportLivePrice(
  marketHashName: string,
  currency: string = 'USD',
): Promise<number | null> {
  const cur = normalizeSkinportCurrency(currency)

  // 1. In-memory cache for this currency
  const cached = _skinportMaps.get(cur)
  if (cached && (Date.now() - cached.fetchedAt) < SKINPORT_MEM_TTL_MS) {
    return cached.map.get(marketHashName)?.price ?? null
  }

  // 2. Redis backup (USD only — we don't persist non-USD maps)
  if (cur === 'USD') {
    try {
      const redisCached = await redis.get<Record<string, { price: number; volume: number }>>(
        `${SKINPORT_MAP_KEY_PREFIX}:USD`,
      )
      if (redisCached) {
        const map = new Map(Object.entries(redisCached))
        _skinportMaps.set('USD', { map, fetchedAt: Date.now() })
        return map.get(marketHashName)?.price ?? null
      }
    } catch { /* ignore */ }
  }

  // 3. Refetch from Skinport API in the requested currency
  const map = await fetchSkinportPrices(undefined, cur)
  return map.get(marketHashName)?.price ?? null
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
// Exchange API (per-item): fetches the real cheapest listing for a given skin.
// Prices are in USD cents → divide by 100.
// Used for on-demand lookups; bulk fallback uses price-aggregator (approximate).

interface DMarketExchangeItem {
  title: string
  price: Record<string, number>  // e.g. { USD: 1234 } or { EUR: 1100 } — minor units
}

const DMARKET_SUPPORTED_CURRENCIES = ['USD', 'EUR', 'GBP', 'AUD', 'CAD', 'JPY'] as const
function normalizeDMarketCurrency(c: string): string {
  const up = c.toUpperCase()
  return (DMARKET_SUPPORTED_CURRENCIES as readonly string[]).includes(up) ? up : 'USD'
}

export async function fetchDMarketLivePrice(
  marketHashName: string,
  currency: string = 'USD',
): Promise<number | null> {
  const cur = normalizeDMarketCurrency(currency)
  try {
    const url = 'https://api.dmarket.com/exchange/v1/market/items'
    const params = { side: 'market', orderBy: 'price', orderDir: 'asc', title: marketHashName, gameId: 'a8db', currency: cur, limit: 1 }
    console.log(`[DMarket] Request: ${marketHashName}, params: ${JSON.stringify(params)}`)
    
    const { data } = await axios.get<{ objects: DMarketExchangeItem[] }>(url, { params, timeout: 10000 })
    console.log(`[DMarket] Response for ${marketHashName}: ${JSON.stringify(data).substring(0, 200)}`)
    
    const item = data.objects?.[0]
    if (!item) {
      console.log(`[DMarket] No item found for: ${marketHashName}`)
      return null
    }
    const minor = item?.price?.[cur]
    if (minor == null) {
      console.log(`[DMarket] No price for ${marketHashName}: ${JSON.stringify(item?.price)}`)
      return null
    }
    console.log(`[DMarket] SUCCESS: ${marketHashName} = ${minor / 100}`)
    return minor / 100
  } catch (e: any) {
    console.log(`[DMarket] Error fetching ${marketHashName}: ${e.message || e}`)
    return null
  }
}

interface DMarketAggregatedItem {
  MarketHashName: string
  Offers: { BestPrice: string; Count: number }
  GameID: string
}

async function fetchDMarketPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  // Use DMarket price-aggregator for bulk prices (fast but can be inflated for rare items)
  // The 5× filter in the save logic handles inflated prices
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
        if (item.GameID !== 'a8db') continue
        const name = item.MarketHashName
        if (!name) continue
        const price = parseFloat(item.Offers?.BestPrice ?? '0')
        if (price > 0) map.set(name, price)
      }

      offset += PAGE_SIZE
    }

    log.info(`[Prices] DMarket: ${map.size} CS2 items`)
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

  // Batch-load the most recent history entry per skin (used for 24h price-change calculation)
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
    await Promise.all(batch.map(async (skin) => {
      const sp  = skinportMap.get(skin.marketHashName)
      const dm  = dmarketMap.get(skin.marketHashName)
      const cgm = csgoMarketMap.get(skin.marketHashName)

      if (!sp && !dm && !cgm) return  // no data for this skin

      const skinportPrice    = sp?.price ?? null
      const rawDmarketPrice  = dm ?? null
      const csgoMarketPrice  = cgm ?? null

      // The DMarket price-aggregator sometimes returns inflated "BestPrice" values
      // (e.g. a single whale-seller listing at $10 000 for a $1 skin).
      // Discard DMarket if it is more than 5× the cheapest of the other two markets.
      const otherMin = calcLowestPrice(skinportPrice, csgoMarketPrice)
      const dmarketPrice = (rawDmarketPrice !== null && otherMin !== null && rawDmarketPrice > otherMin * 5)
        ? null
        : rawDmarketPrice

      const volume = sp?.volume ?? 0
      const lowestPrice = calcLowestPrice(skinportPrice, dmarketPrice, csgoMarketPrice)

      if (!lowestPrice) return

      const oldPrice = oldPriceMap.get(skin.id) ?? null
      const priceChange24h = oldPrice && oldPrice > 0
        ? ((lowestPrice - oldPrice) / oldPrice) * 100
        : null

      await prisma.skinPrice.upsert({
        where: { skinId: skin.id },
        update: {
          skinportPrice,
          dmarketPrice,
          csgoMarketPrice,
          lowestPrice,
          volume24h: volume,
          priceChange24h,
          updatedAt: new Date(),
        },
        create: {
          skinId: skin.id,
          skinportPrice,
          dmarketPrice,
          csgoMarketPrice,
          lowestPrice,
          volume24h: volume,
          priceChange24h,
        },
      })
      updated++
    }))
  }

  // Save price history — max once per 24h per skin to avoid DB bloat
  const recentIds = new Set(
    (await prisma.priceHistory.findMany({
      where: { timestamp: { gte: new Date(Date.now() - 24 * 60 * 60 * 1000) } },
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
      const spPrice  = sp?.price ?? null
      const cgmPrice = cgm ?? null
      const otherMin = calcLowestPrice(spPrice, cgmPrice)
      const dmPrice  = (dm !== undefined && dm !== null && otherMin !== null && dm > otherMin * 5) ? null : (dm ?? null)
      const lowestPrice = calcLowestPrice(spPrice, dmPrice, cgmPrice)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })

  if (historyRows.length > 0) {
    await prisma.priceHistory.createMany({ data: historyRows })
  }

  await redis.del('top-movers:20')
  log.info(`[PricePopulate] Done — ${updated} skins updated, ${historyRows.length} history entries saved`)
}
