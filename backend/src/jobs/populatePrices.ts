import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { env } from '../config/env'
import type { FastifyBaseLogger } from 'fastify'

// ─── CS2Cap ───────────────────────────────────────────────────────────────────
// Price aggregator that consolidates cheapest listings across 38+ marketplaces.
// Bulk endpoint: POST /prices/batch (up to 100 items per call, prices in cents).
// Live endpoint: GET /prices?market_hash_name=... (single item, prices in cents).
// Requires CS2CAP_API_KEY env var — skipped gracefully when not set.

const CS2CAP_BASE = 'https://api.cs2c.app/v1'

async function fetchCS2CapBulkPrices(
  names: string[],
  log?: FastifyBaseLogger,
): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  if (!env.CS2CAP_API_KEY) {
    log?.warn('[Prices] CS2CAP_API_KEY not set — skipping CS2Cap bulk fetch')
    return map
  }

  const BATCH = 100
  const CONCURRENCY = 5
  const batches: string[][] = []
  for (let i = 0; i < names.length; i += BATCH) batches.push(names.slice(i, i + BATCH))

  // Run up to CONCURRENCY batch requests in parallel
  for (let i = 0; i < batches.length; i += CONCURRENCY) {
    const chunk = batches.slice(i, i + CONCURRENCY)
    const results = await Promise.allSettled(
      chunk.map(async (batch) => {
        const { data } = await axios.post(
          `${CS2CAP_BASE}/prices/batch`,
          { names: batch },
          {
            headers: { Authorization: `Bearer ${env.CS2CAP_API_KEY}` },
            timeout: 30000,
          },
        )
        return data as Record<string, { lowest_ask?: number }>
      }),
    )
    for (const result of results) {
      if (result.status !== 'fulfilled') continue
      for (const [name, priceData] of Object.entries(result.value)) {
        const ask = priceData?.lowest_ask
        if (typeof ask === 'number' && ask > 0) {
          map.set(name, ask / 100) // cents → USD
        }
      }
    }
  }

  log?.info(`[Prices] CS2Cap: ${map.size} items with prices`)
  return map
}

/**
 * Live per-skin CS2Cap price. Returns the aggregated lowest ask across all
 * integrated marketplaces (prices in cents on the API → converted to USD).
 */
export async function fetchCS2CapLivePrice(marketHashName: string): Promise<number | null> {
  if (!env.CS2CAP_API_KEY) return null
  try {
    const { data } = await axios.get(`${CS2CAP_BASE}/prices`, {
      params: { market_hash_name: marketHashName },
      headers: { Authorization: `Bearer ${env.CS2CAP_API_KEY}` },
      timeout: 10000,
    })
    const ask = data?.lowest_ask
    if (typeof ask !== 'number' || ask <= 0) return null
    return ask / 100
  } catch {
    return null
  }
}

// ─── CSFloat ──────────────────────────────────────────────────────────────────
// Float-verified CS2 marketplace. Live per-item lookup via exchange listings API.
// Prices are in USD cents — divide by 100.
// Requires CSFLOAT_API_KEY env var — skipped gracefully when not set.

const CSFLOAT_BASE = 'https://csfloat.com/api/v1'

interface CSFloatListing {
  price: number  // USD cents
}

/**
 * Live cheapest CSFloat listing for a skin.
 * Uses sort_by=lowest_price with limit=1 to get the single cheapest listing.
 */
export async function fetchCSFloatLivePrice(marketHashName: string): Promise<number | null> {
  if (!env.CSFLOAT_API_KEY) return null
  try {
    const { data } = await axios.get<{ data: CSFloatListing[] }>(`${CSFLOAT_BASE}/listings`, {
      params: { market_hash_name: marketHashName, sort_by: 'lowest_price', limit: 1 },
      headers: { Authorization: env.CSFLOAT_API_KEY },
      timeout: 10000,
    })
    const cents = data.data?.[0]?.price
    if (typeof cents !== 'number' || cents <= 0) return null
    return cents / 100
  } catch {
    return null
  }
}

// ─── CS:GO Market ────────────────────────────────────────────────────────────
// market.csgo.com free public endpoint — 25k+ items, price in USD as a string.

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

// ─── Merge & persist ─────────────────────────────────────────────────────────

function calcLowestPrice(...prices: (number | null | undefined)[]): number | null {
  const valid = prices.filter((p): p is number => p != null && p > 0)
  return valid.length > 0 ? Math.min(...valid) : null
}

export async function populatePrices(log: FastifyBaseLogger): Promise<void> {
  log.info('[PricePopulate] Fetching prices from CS2Cap and CS:GO Market...')

  // Load all DB skins once for CS2Cap bulk batch
  const skins = await prisma.skin.findMany({ select: { id: true, marketHashName: true } })
  const names = skins.map((s) => s.marketHashName)

  const [cs2capMap, csgoMarketMap] = await Promise.all([
    fetchCS2CapBulkPrices(names, log),
    fetchCsgoMarketPrices(log),
  ])

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
    await Promise.all(batch.map(async (skin) => {
      const cs2cap = cs2capMap.get(skin.marketHashName) ?? null
      const cgm    = csgoMarketMap.get(skin.marketHashName) ?? null

      if (!cs2cap && !cgm) return

      // CS2Cap is already an aggregated lowest ask — no inflation sanity filter needed.
      const lowestPrice = calcLowestPrice(cs2cap, cgm)
      if (!lowestPrice) return

      const oldPrice = oldPriceMap.get(skin.id) ?? null
      const priceChange24h = oldPrice && oldPrice > 0
        ? ((lowestPrice - oldPrice) / oldPrice) * 100
        : null

      await prisma.skinPrice.upsert({
        where: { skinId: skin.id },
        update: {
          cs2capPrice: cs2cap,
          csgoMarketPrice: cgm,
          lowestPrice,
          priceChange24h,
          updatedAt: new Date(),
        },
        create: {
          skinId: skin.id,
          cs2capPrice: cs2cap,
          csgoMarketPrice: cgm,
          lowestPrice,
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
      const cs2cap = cs2capMap.get(s.marketHashName) ?? null
      const cgm    = csgoMarketMap.get(s.marketHashName) ?? null
      const lowestPrice = calcLowestPrice(cs2cap, cgm)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })

  if (historyRows.length > 0) {
    await prisma.priceHistory.createMany({ data: historyRows })
  }

  await redis.del('top-movers:20')
  log.info(`[PricePopulate] Done — ${updated} skins updated, ${historyRows.length} history entries saved`)
}
