import { Prisma } from '@prisma/client'
import { prisma } from '../db/prisma'
import { CHANGE_REFERENCE_WINDOW_MS } from '../config/priceHistory'
import { CSFLOAT_IN_LOWEST_FROM } from '../config/priceQuality'
import { lowestPriceAt, type MarketplaceQuotes } from './quotes'

/** A skin's reference point for the 24h change: its price, and when it was written. */
export interface ReferencePrice {
  price: number
  at: Date
}

/**
 * Each skin's reference price for the 24h change: its latest PriceHistory point
 * inside [24h ago − CHANGE_REFERENCE_WINDOW_MS, 24h ago]. The one place that
 * resolves it, so search, detail, top movers, the watchlist batch and the bulk
 * job can't disagree about the same skin — the detail route used to search with
 * no lower bound and showed a different change than the list it was opened from.
 *
 * Raw `DISTINCT ON` because Prisma's `distinct` can't be pushed down under a
 * timestamp `orderBy`: it fetches every row in the window and dedupes
 * in-process, ~4x the rows on a metered connection. One row per skin crosses
 * the wire, and only the two columns used.
 *
 * Kept out of services/prices.ts so routes can use it without pulling in Redis.
 */
export async function fetchReferencePrices(skinIds: string[]): Promise<Map<string, ReferencePrice>> {
  if (skinIds.length === 0) return new Map()
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const windowStart = new Date(dayAgo.getTime() - CHANGE_REFERENCE_WINDOW_MS)
  const rows = await prisma.$queryRaw<Array<{ skinId: string; price: number; at: Date }>>`
    SELECT DISTINCT ON ("skinId") "skinId", "price", "timestamp" AS "at"
    FROM "PriceHistory"
    WHERE "skinId" IN (${Prisma.join(skinIds)})
      AND "timestamp" >= ${windowStart}
      AND "timestamp" <= ${dayAgo}
    ORDER BY "skinId", "timestamp" DESC
  `
  return new Map(rows.map((r) => [r.skinId, { price: r.price, at: r.at }]))
}

/**
 * Today's price on the same basis as `ref`. A reference written before CSFloat
 * joined `lowestPrice` was the minimum of the other three; comparing it with a
 * minimum that now includes CSFloat would show a drop on every skin CSFloat
 * undercuts. See CSFLOAT_IN_LOWEST_FROM.
 */
export function comparablePrice(
  row: MarketplaceQuotes & { lowestPrice: number | null },
  ref: ReferencePrice,
): number | null {
  return ref.at.getTime() < CSFLOAT_IN_LOWEST_FROM ? lowestPriceAt(row, ref.at.getTime()) : row.lowestPrice
}

/** The 24h change for a SkinPrice row against its reference, or null without one. */
export function change24h(
  row: MarketplaceQuotes & { lowestPrice: number | null },
  ref: ReferencePrice | undefined,
): number | null {
  if (!ref) return null
  return percentChange(comparablePrice(row, ref), ref.price)
}

/** Percentage change from `reference` to `current`, or null when either is unusable. */
export function percentChange(current: number | null, reference: number | null | undefined): number | null {
  if (current === null || reference == null || reference <= 0) return null
  return ((current - reference) / reference) * 100
}
