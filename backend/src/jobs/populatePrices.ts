import axios from 'axios'
import zlib from 'zlib'
import { promisify } from 'util'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import { CHANGE_REFERENCE_WINDOW_MS } from '../config/priceHistory'
import type { FastifyBaseLogger } from 'fastify'

const brotliDecompress = promisify(zlib.brotliDecompress)

// ─── Bulk price fetchers ──────────────────────────────────────────────────────
// Strategy: each marketplace exposes a single bulk endpoint that returns the
// entire CS2 catalog in one HTTP call. We fetch all of them in parallel every
// 2h. With 12 calls/day per marketplace, rate limits are a non-issue.
//
// All endpoints below are PUBLIC and require NO authentication:
//   • Skinport      — api.skinport.com/v1/items
//   • CS:GO Market  — market.csgo.com/api/v2/prices/USD.json
//   • Waxpeer       — api.waxpeer.com/v1/prices
//
// CSDeals and DMarket dropped (2026-07): CSDeals' bulk endpoint only lists
// ~2.6k actively-stocked items (vs ~20-25k for the other marketplaces), so it
// almost never matched anything outside the most common skins. DMarket's
// public bulk aggregator was retired outright — its replacement requires
// signed API-key auth and per-title lookups, incompatible with the no-keys
// bulk-fetch design here.

// ─── Skinport ────────────────────────────────────────────────────────────────

interface SkinportItem {
  market_hash_name: string
  min_price: number | null  // USD, lowest current listing
  suggested_price: number | null
}

async function fetchSkinportPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    // Skinport requires Brotli compression — fetch as arraybuffer and decompress manually
    // because axios/Node http doesn't auto-decompress Brotli.
    const { data } = await axios.get<Buffer>(
      'https://api.skinport.com/v1/items',
      {
        params: { app_id: 730, currency: 'USD', tradable: 0 },
        timeout: 30000,
        headers: { 'Accept-Encoding': 'br' },
        responseType: 'arraybuffer',
        decompress: false,
      },
    )
    const decompressed = await brotliDecompress(Buffer.from(data))
    const items: SkinportItem[] = JSON.parse(decompressed.toString('utf8'))
    for (const item of items ?? []) {
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

// ─── Waxpeer ─────────────────────────────────────────────────────────────────
// Bulk endpoint, no auth, ~20k items. `min` is USD × 1000 (e.g. 32079 → $32.079).

interface WaxpeerItem {
  name: string
  min: number
}

interface WaxpeerResponse {
  success: boolean
  items: WaxpeerItem[]
}

async function fetchWaxpeerPrices(log: FastifyBaseLogger): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  try {
    const { data } = await axios.get<WaxpeerResponse>(
      'https://api.waxpeer.com/v1/prices',
      {
        params: { game: 'csgo' },
        timeout: 30000,
      },
    )
    if (!data?.success || !data.items) {
      log.warn('[Prices] Waxpeer: response not in expected format')
      return map
    }
    for (const item of data.items) {
      const price = item.min / 1000
      if (price > 0 && item.name) {
        map.set(item.name, price)
      }
    }
    log.info(`[Prices] Waxpeer: ${map.size} items`)
  } catch (err) {
    log.warn(`[Prices] Waxpeer failed: ${(err as Error).message}`)
  }
  return map
}

// ─── Merge & persist ─────────────────────────────────────────────────────────

function calcLowestPrice(...prices: (number | null | undefined)[]): number | null {
  const valid = prices.filter((p): p is number => p != null && p > 0)
  return valid.length > 0 ? Math.min(...valid) : null
}

// ─── Pipeline freshness ──────────────────────────────────────────────────────
// `/health` reports whether prices are still moving, not just whether the
// process answers. Deliberately outside the `prices:*` namespace: app.ts wipes
// that glob on every boot, which would blank the marker on each deploy.
export const PRICE_RUN_TIMESTAMP_KEY = 'pipeline:prices:lastSuccessfulRun'

// The bulk job runs every 6h, so 8h is one run plus slack: a single failed run
// alerts, a slow one doesn't.
const PRICE_STALE_AFTER_MS = 8 * 60 * 60 * 1000

// 'unknown' covers both a Redis outage and a never-yet-run pipeline. Neither is
// 'fresh', so the keyword monitor alerts on them — which is what we want.
export type PriceFreshness = 'fresh' | 'stale' | 'unknown'

export interface PriceHealth {
  freshness: PriceFreshness
  lastRun: Date | null
}

export async function getPriceHealth(): Promise<PriceHealth> {
  try {
    // Upstash deserializes JSON, but a value written by an older/raw client can
    // still come back as a string.
    const raw = await redis.get(PRICE_RUN_TIMESTAMP_KEY)
    const last = typeof raw === 'string' ? Number(raw) : raw
    if (typeof last !== 'number' || !Number.isFinite(last)) {
      return { freshness: 'unknown', lastRun: null }
    }
    return {
      freshness: Date.now() - last < PRICE_STALE_AFTER_MS ? 'fresh' : 'stale',
      lastRun: new Date(last),
    }
  } catch {
    return { freshness: 'unknown', lastRun: null }
  }
}

export interface PopulatePricesSummary {
  updated: number
  historyRows: number
}

export async function populatePrices(log: FastifyBaseLogger): Promise<PopulatePricesSummary> {
  log.info('[PricePopulate] Fetching prices from 3 marketplaces in parallel...')

  const [skinportMap, csgoMarketMap, waxpeerMap] = await Promise.all([
    fetchSkinportPrices(log),
    fetchCsgoMarketPrices(log),
    fetchWaxpeerPrices(log),
  ])

  const skins = await prisma.skin.findMany({ select: { id: true, marketHashName: true } })
  log.info(`[PricePopulate] Merging prices for ${skins.length} skins...`)

  // Batch-load previous price entries for 24h change calculation
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const windowStart = new Date(dayAgo.getTime() - CHANGE_REFERENCE_WINDOW_MS)
  const recentHistory = await prisma.priceHistory.findMany({
    where: { timestamp: { gte: windowStart, lte: dayAgo } },
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
        const waxpeer  = waxpeerMap.get(skin.marketHashName) ?? null

        if (!skinport && !csgo && !waxpeer) return

        const lowestPrice = calcLowestPrice(skinport, csgo, waxpeer)
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
            waxpeerPrice:    waxpeer,
            lowestPrice,
            priceChange24h,
            updatedAt: new Date(),
          },
          create: {
            skinId: skin.id,
            skinportPrice:   skinport,
            csgoMarketPrice: csgo,
            waxpeerPrice:    waxpeer,
            lowestPrice,
            priceChange24h,
          },
        })
        updated++
      }),
    )
  }

  // Save a price history point for every skin on every run (every 2h).
  const historyRows = skins
    .flatMap((s) => {
      const skinport = skinportMap.get(s.marketHashName) ?? null
      const csgo     = csgoMarketMap.get(s.marketHashName) ?? null
      const waxpeer  = waxpeerMap.get(s.marketHashName) ?? null
      const lowestPrice = calcLowestPrice(skinport, csgo, waxpeer)
      if (!lowestPrice) return []
      return [{ skinId: s.id, price: lowestPrice, source: 'bulk' }]
    })

  if (historyRows.length > 0) {
    await prisma.priceHistory.createMany({ data: historyRows })
  }

  await redis.del('top-movers:20')

  // Only a run that actually moved prices counts as successful. Each fetcher
  // swallows its own errors and returns an empty map, so all three marketplaces
  // breaking at once still reaches this line — without this guard /health would
  // report 'fresh' while serving frozen prices, which is the exact failure the
  // freshness marker exists to catch.
  if (updated > 0) {
    await redis.set(PRICE_RUN_TIMESTAMP_KEY, Date.now())
  } else {
    log.error(
      '[PricePopulate] No skins updated — every marketplace returned nothing. Leaving the freshness marker untouched; /health will report stale.',
    )
  }

  log.info(`[PricePopulate] Done — ${updated} skins updated, ${historyRows.length} history entries saved`)

  return { updated, historyRows: historyRows.length }
}
