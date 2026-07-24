import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { CHANGE_REFERENCE_WINDOW_MS } from '../config/priceHistory'
import {
  MAX_TOP_MOVER_QUOTE_SPREAD,
  MIN_TOP_MOVER_ABS_MOVE,
  MIN_TOP_MOVER_PRICE,
} from '../config/priceQuality'
import type { AggregatedPrices } from '../types'

/**
 * How far apart the marketplaces are on the same skin, as a ratio of dearest to
 * cheapest. 1 means they agree exactly; fewer than two quotes means the question
 * doesn't apply, and Infinity keeps such a row out of anything that filters on
 * agreement rather than letting it through on a technicality.
 */
function quoteSpread(prices: {
  skinportPrice: number | null
  csgoMarketPrice: number | null
  waxpeerPrice: number | null
}): number {
  const quotes = [prices.skinportPrice, prices.csgoMarketPrice, prices.waxpeerPrice].filter(
    (p): p is number => p !== null && p > 0,
  )
  if (quotes.length < 2) return Infinity
  return Math.max(...quotes) / Math.min(...quotes)
}

// ─── Top-movers cache ────────────────────────────────────────────────────────
// The key format and the invalidation glob are derived from one prefix on
// purpose. They used to be independent: the writer's key grew a `direction`
// segment and the three invalidation sites went on deleting `top-movers:20`,
// a key nobody had written since — so a bulk price run left the cache in place
// and top-movers stayed stale until the 15-min TTL expired. Nothing errored,
// which is exactly why this must be structural rather than a comment.
const TOP_MOVERS_CACHE_PREFIX = 'top-movers'

function topMoversCacheKey(direction: 'rising' | 'falling', limit: number): string {
  return `${TOP_MOVERS_CACHE_PREFIX}:${direction}:${limit}`
}

/**
 * Drops every cached top-movers list. Call after anything that moves the prices
 * underneath them — the bulk price run, a catalog import.
 *
 * Globs rather than enumerating (direction × limit): the route pins both today,
 * but `getTopMovers` takes them as parameters, so a second caller with a
 * different limit would silently escape a hardcoded list.
 */
export async function invalidateTopMoversCache(): Promise<number> {
  const keys = await redis.keys(`${TOP_MOVERS_CACHE_PREFIX}:*`)
  if (keys.length === 0) return 0
  await Promise.all(keys.map((key) => redis.del(key)))
  return keys.length
}

export class PriceService {

  /** Read stored prices from DB (populated by bulk job every 6h). Cached 5 min. */
  async getPricesForSkin(skinId: string, marketHashName: string): Promise<AggregatedPrices> {
    const cacheKey = `prices:${skinId}`
    const cached = await redis.get<AggregatedPrices>(cacheKey)
    if (cached) return cached

    const row = await prisma.skinPrice.findUnique({ where: { skinId } })
    const priceChange24h = await this.calculate24hChange(skinId, row?.lowestPrice ?? null)

    const prices: AggregatedPrices = {
      skinId,
      marketHashName,
      skinportPrice:   row?.skinportPrice   ?? null,
      csgoMarketPrice: row?.csgoMarketPrice ?? null,
      waxpeerPrice:    row?.waxpeerPrice    ?? null,
      lowestPrice:     row?.lowestPrice     ?? null,
      priceChange24h,
      volume24h:       row?.volume24h       ?? null,
      updatedAt:       row?.updatedAt.toISOString() ?? new Date().toISOString(),
    }

    await redis.set(cacheKey, prices, { ex: CACHE_TTL.SKIN_PRICES })
    return prices
  }

  async calculate24hChange(skinId: string, currentPrice: number | null): Promise<number | null> {
    if (currentPrice === null) return null

    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const record = await prisma.priceHistory.findFirst({
      where: { skinId, timestamp: { lte: dayAgo } },
      orderBy: { timestamp: 'desc' },
    })

    if (!record) return null
    return ((currentPrice - record.price) / record.price) * 100
  }

  /**
   * Top movers: skins with the highest (or lowest) 24h price growth, filtered
   * for signal quality.
   *
   * Pure DB read — no external API calls. The bulk job keeps both `lowestPrice`
   * and `priceChange24h` fresh every 6h, and `priceChange24h` is indexed for
   * fast sorting. We then refine the values from the more accurate `PriceHistory`
   * table to handle prices that moved between bulk runs, and drop rows that
   * don't clear a minimum price ($1) and minimum absolute 24h change ($0.25) —
   * below those thresholds the "movement" is almost always marketplace spread
   * noise rather than a real, flippable price move.
   *
   * The dominant filter, though, is corroboration. A 24h change is only as
   * trustworthy as the price it was computed from, and a price no second
   * marketplace quotes cannot be checked against anything. Measured 2026-07-24:
   * single-quote skins are 3.7% of the catalog but produced 15 of the top 20
   * risers, led by a Battle-Scarred M4A4 | Bullet Rain at $61 938 and +50 608%.
   * See `config/priceQuality.ts`.
   */
  async getTopMovers(
    direction: 'rising' | 'falling' = 'rising',
    limit = 20
  ): Promise<Array<AggregatedPrices & { name: string; iconUrl: string }>> {
    const cacheKey = topMoversCacheKey(direction, limit)
    const cached = await redis.get<Array<AggregatedPrices & { name: string; iconUrl: string }>>(cacheKey)
    if (cached) return cached

    const sortOrder = direction === 'falling' ? 'asc' : 'desc'

    // Pre-sorted by DB priceChange24h (indexed). Candidate batch is wider than
    // `limit` because the quality filter below can discard rows (e.g. no
    // PriceHistory entry from ~24h ago, or the move is too small in absolute
    // terms) and we don't want to under-fill the final list.
    //
    // The MIN_TOP_MOVER_QUOTES rule is expressed in SQL rather than alongside
    // the others below because it disqualified most of what used to reach the
    // top of the candidate batch: filtering it in-process would mean paging
    // through mostly-rejected rows, and every one of them crosses the wire on a
    // metered connection. Enumerating the pairs is the honest way to say "at
    // least two of these three are non-null" — Prisma has no counting operator
    // over sibling columns, and a raw query here would give up the typed
    // include on `price`.
    const atLeastTwoQuotes = [
      { skinportPrice: { not: null }, csgoMarketPrice: { not: null } },
      { skinportPrice: { not: null }, waxpeerPrice: { not: null } },
      { csgoMarketPrice: { not: null }, waxpeerPrice: { not: null } },
    ]

    const skins = await prisma.skin.findMany({
      include: { price: true },
      where: { price: { lowestPrice: { gte: MIN_TOP_MOVER_PRICE }, OR: atLeastTwoQuotes } },
      orderBy: [
        { price: { priceChange24h: { sort: sortOrder, nulls: 'last' } } },
        { price: { lowestPrice: 'desc' } },
      ],
      take: Math.max(limit * 5, 100),
    })

    const skinIds = skins.map((s) => s.id)
    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const windowStart = new Date(dayAgo.getTime() - CHANGE_REFERENCE_WINDOW_MS)
    const histories = await prisma.priceHistory.findMany({
      where: { skinId: { in: skinIds }, timestamp: { gte: windowStart, lte: dayAgo } },
      orderBy: { timestamp: 'desc' },
      distinct: ['skinId'],
    })
    const historyMap = new Map(histories.map((h) => [h.skinId, h.price]))

    const topMovers = skins
      .map((skin) => {
        const current = skin.price?.lowestPrice ?? null
        const prev = historyMap.get(skin.id) ?? null
        const priceChange24h =
          current !== null && prev !== null && prev > 0
            ? ((current - prev) / prev) * 100
            : null
        return {
          skinId: skin.id,
          marketHashName: skin.marketHashName,
          name: skin.name,
          iconUrl: skin.iconUrl,
          skinportPrice:   skin.price?.skinportPrice   ?? null,
          csgoMarketPrice: skin.price?.csgoMarketPrice ?? null,
          waxpeerPrice:    skin.price?.waxpeerPrice    ?? null,
          lowestPrice: current,
          priceChange24h,
          volume24h: skin.price?.volume24h ?? null,
          updatedAt: skin.price?.updatedAt.toISOString() ?? new Date().toISOString(),
          _prev: prev,
        }
      })
      // Quality filter: need a real 24h-ago reference price and a move big
      // enough to be worth acting on. Without `_prev` we can't tell noise from
      // a real move; below MIN_TOP_MOVER_ABS_MOVE there is nothing to act on
      // even when the percentage is dramatic.
      .filter(
        (s) =>
          s._prev !== null &&
          s.lowestPrice !== null &&
          Math.abs(s.lowestPrice - s._prev) >= MIN_TOP_MOVER_ABS_MOVE,
      )
      // Corroboration, part two. The SQL above guaranteed a second quote exists;
      // this asks whether the quotes actually agree. They routinely don't: the
      // same skin listed at $373 and $2 762 across two marketplaces has no
      // meaningful "price", so its 24h change is not a fact worth ranking.
      .filter((s) => quoteSpread(s) <= MAX_TOP_MOVER_QUOTE_SPREAD)
      .sort((a, b) => {
        const diff =
          direction === 'falling'
            ? (a.priceChange24h ?? Infinity) - (b.priceChange24h ?? Infinity)
            : (b.priceChange24h ?? -Infinity) - (a.priceChange24h ?? -Infinity)
        if (diff !== 0) return diff
        return (b.lowestPrice ?? 0) - (a.lowestPrice ?? 0)
      })
      .slice(0, limit)
      .map(({ _prev, ...rest }) => rest)

    if (topMovers.length > 0) {
      await redis.set(cacheKey, topMovers, { ex: CACHE_TTL.TOP_MOVERS })
    }
    return topMovers
  }
}
