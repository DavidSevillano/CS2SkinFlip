import axios from 'axios'
import zlib from 'zlib'
import { randomUUID } from 'crypto'
import { promisify } from 'util'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import { CHANGE_REFERENCE_WINDOW_MS } from '../config/priceHistory'
import { invalidateTopMoversCache } from '../services/prices'
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

interface SkinPriceRow {
  skinId: string
  skinport: number | null
  csgo: number | null
  waxpeer: number | null
  lowestPrice: number
  priceChange24h: number | null
}

// 500 rows x 8 columns = 4000 bind parameters, comfortably under Postgres' 65535
// cap. The catalog is ~24k skins but only ~13.2k carry a price from any
// marketplace (measured 2026-07-22), so a full run is ~27 statements.
const WRITE_BATCH = 500

const SKIN_PRICE_COLUMNS = 8

/**
 * Upserts every priced skin in batched multi-row statements, returning the number
 * of rows written.
 *
 * This used to be one `prisma.skinPrice.upsert()` per skin: ~13.2k sequential
 * round trips per run, 4 runs a day, each returning the full row Prisma insists
 * on selecting back. The row payloads were never the problem — the per-round-trip
 * protocol and TLS framing was, against a Neon free-tier network transfer budget
 * this job was on course to eat a large share of. Batching collapses it to ~27
 * statements that return nothing.
 *
 * Raw SQL with explicit `$n` placeholders rather than the tidier `unnest(...)`
 * of array parameters: half these columns are nullable floats, and an array
 * literal mixing numbers and NULLs leaves Postgres to infer an element type from
 * the parameter rather than the target column. Scalar placeholders take their
 * type from the INSERT target and sidestep the question.
 */
async function writeSkinPrices(rows: SkinPriceRow[]): Promise<number> {
  if (rows.length === 0) return 0

  const now = new Date()
  let written = 0

  for (let i = 0; i < rows.length; i += WRITE_BATCH) {
    const batch = rows.slice(i, i + WRITE_BATCH)

    const tuples = batch
      .map((_, n) => {
        const b = n * SKIN_PRICE_COLUMNS
        return `($${b + 1},$${b + 2},$${b + 3},$${b + 4},$${b + 5},$${b + 6},$${b + 7},$${b + 8})`
      })
      .join(',')

    const values = batch.flatMap((r) => [
      // `@default(cuid())` is generated by Prisma Client, which raw SQL bypasses,
      // so the id has to come from here. The column is text and nothing ever looks
      // a row up by it — `skinId` carries the unique constraint and every read —
      // so the format only has to be unique, not cuid-shaped.
      randomUUID(),
      r.skinId,
      r.skinport,
      r.csgo,
      r.waxpeer,
      r.lowestPrice,
      r.priceChange24h,
      now,
    ])

    // `volume24h` is deliberately absent from both the column list and the SET
    // clause: this job never computes it, and naming it here would overwrite
    // whatever else populated it with NULL on every run.
    await prisma.$executeRawUnsafe(
      `INSERT INTO "SkinPrice" (
         "id","skinId","skinportPrice","csgoMarketPrice","waxpeerPrice","lowestPrice","priceChange24h","updatedAt"
       ) VALUES ${tuples}
       ON CONFLICT ("skinId") DO UPDATE SET
         "skinportPrice"   = EXCLUDED."skinportPrice",
         "csgoMarketPrice" = EXCLUDED."csgoMarketPrice",
         "waxpeerPrice"    = EXCLUDED."waxpeerPrice",
         "lowestPrice"     = EXCLUDED."lowestPrice",
         "priceChange24h"  = EXCLUDED."priceChange24h",
         "updatedAt"       = EXCLUDED."updatedAt"`,
      ...values,
    )

    written += batch.length
  }

  return written
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

  // Batch-load previous price entries for 24h change calculation.
  //
  // Raw `DISTINCT ON` rather than Prisma's `distinct`, which it cannot push down
  // to Postgres under a timestamp `orderBy` — it fetches every row in the window
  // and dedupes in-process. The window holds one write per skin per refresh, so
  // that is ~4x the rows we keep, pulled across the wire every run for nothing.
  //
  // Measured on production (EXPLAIN, 2026-07-22): 54_786 rows in the window
  // deduping to 13_252. The plan is an index scan on [timestamp] feeding an
  // explicit Sort — the [skinId, timestamp] index can't serve a predicate that
  // filters on timestamp alone, so it does NOT avoid the sort. That's fine and
  // deliberate: the sort is the database's to pay, and what this saves is the
  // 4x on rows crossing the wire, not planner work.
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const windowStart = new Date(dayAgo.getTime() - CHANGE_REFERENCE_WINDOW_MS)
  const recentHistory = await prisma.$queryRaw<{ skinId: string; price: number }[]>`
    SELECT DISTINCT ON ("skinId") "skinId", "price"
    FROM "PriceHistory"
    WHERE "timestamp" >= ${windowStart} AND "timestamp" <= ${dayAgo}
    ORDER BY "skinId", "timestamp" DESC
  `
  const oldPriceMap = new Map(recentHistory.map((h) => [h.skinId, h.price]))

  const rows = skins.flatMap((skin) => {
    const skinport = skinportMap.get(skin.marketHashName) ?? null
    const csgo     = csgoMarketMap.get(skin.marketHashName) ?? null
    const waxpeer  = waxpeerMap.get(skin.marketHashName) ?? null

    if (!skinport && !csgo && !waxpeer) return []

    const lowestPrice = calcLowestPrice(skinport, csgo, waxpeer)
    if (!lowestPrice) return []

    const oldPrice = oldPriceMap.get(skin.id) ?? null
    const priceChange24h = oldPrice && oldPrice > 0
      ? ((lowestPrice - oldPrice) / oldPrice) * 100
      : null

    return [{ skinId: skin.id, skinport, csgo, waxpeer, lowestPrice, priceChange24h }]
  })

  const updated = await writeSkinPrices(rows)

  // Save a price history point for every priced skin on every run. Derived from
  // the same `rows` the upsert used rather than re-deriving from the marketplace
  // maps, so the history point can't disagree with the price just written.
  const historyRows = rows.map((r) => ({ skinId: r.skinId, price: r.lowestPrice, source: 'bulk' }))

  if (historyRows.length > 0) {
    await prisma.priceHistory.createMany({ data: historyRows })
  }

  await invalidateTopMoversCache()

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
