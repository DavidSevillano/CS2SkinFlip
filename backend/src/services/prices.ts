import { Prisma } from '@prisma/client'
import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { change24h, comparablePrice, fetchReferencePrices, percentChange } from './priceReference'
import { MIN_TOP_MOVER_ABS_MOVE, MIN_TOP_MOVER_LISTINGS, MIN_TOP_MOVER_PRICE } from '../config/priceQuality'
import { isCorroborated } from './quotes'
import type { AggregatedPrices } from '../types'

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

// ─── Per-skin price cache ────────────────────────────────────────────────────
// Same discipline as the top-movers keys: the writer below and the invalidator
// derive from one prefix.
const SKIN_PRICE_CACHE_PREFIX = 'prices'

/** Drops every cached per-skin price — after a bulk run, and on boot. */
export async function invalidateSkinPriceCache(): Promise<number> {
  const keys = await redis.keys(`${SKIN_PRICE_CACHE_PREFIX}:*`)
  if (keys.length === 0) return 0
  await Promise.all(keys.map((key) => redis.del(key)))
  return keys.length
}

// The marketplace columns of SkinPrice, for rules phrased over "any two of them".
const QUOTE_COLUMNS = ['skinportPrice', 'csgoMarketPrice', 'waxpeerPrice', 'csfloatPrice'] as const

export class PriceService {

  /** Read stored prices from DB (populated by bulk job every 6h). Cached 5 min. */
  async getPricesForSkin(skinId: string, marketHashName: string): Promise<AggregatedPrices> {
    const cacheKey = `${SKIN_PRICE_CACHE_PREFIX}:${skinId}`
    const cached = await redis.get<AggregatedPrices>(cacheKey)
    if (cached) return cached

    const row = await prisma.skinPrice.findUnique({ where: { skinId } })
    const priceChange24h = row ? await this.calculate24hChange(row) : null

    const prices: AggregatedPrices = {
      skinId,
      marketHashName,
      skinportPrice:   row?.skinportPrice   ?? null,
      csgoMarketPrice: row?.csgoMarketPrice ?? null,
      waxpeerPrice:    row?.waxpeerPrice    ?? null,
      csfloatPrice:    row?.csfloatPrice    ?? null,
      lowestPrice:     row?.lowestPrice     ?? null,
      priceChange24h,
      volume24h:       row?.volume24h       ?? null,
      updatedAt:       row?.updatedAt.toISOString() ?? new Date().toISOString(),
    }

    await redis.set(cacheKey, prices, { ex: CACHE_TTL.SKIN_PRICES })
    return prices
  }

  async calculate24hChange(row: Parameters<typeof change24h>[0] & { skinId: string }): Promise<number | null> {
    if (row.lowestPrice === null) return null
    const references = await fetchReferencePrices([row.skinId])
    return change24h(row, references.get(row.skinId))
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
  ): Promise<Array<AggregatedPrices & { name: string; iconUrl: string; rarity: string }>> {
    const cacheKey = topMoversCacheKey(direction, limit)
    const cached = await redis.get<Array<AggregatedPrices & { name: string; iconUrl: string; rarity: string }>>(cacheKey)
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
    const atLeastTwoQuotes = QUOTE_COLUMNS.flatMap((a, i) =>
      QUOTE_COLUMNS.slice(i + 1).map((b) => ({ [a]: { not: null }, [b]: { not: null } })),
    ) as Prisma.SkinPriceWhereInput[]

    const skins = await prisma.skin.findMany({
      include: { price: true },
      // The listing floor is SQL for the same reason: in-process, on 100
      // candidates, it starved the list to 4 of 20 (measure-mover-liquidity.ts).
      where: {
        price: {
          lowestPrice: { gte: MIN_TOP_MOVER_PRICE },
          volume24h: { gte: MIN_TOP_MOVER_LISTINGS },
          OR: atLeastTwoQuotes,
        },
      },
      orderBy: [
        { price: { priceChange24h: { sort: sortOrder, nulls: 'last' } } },
        { price: { lowestPrice: 'desc' } },
      ],
      take: Math.max(limit * 5, 100),
    })

    // Measured with pg_stat_statements 2026-07-24: this lookup was the app's
    // heaviest query by rows returned until it moved to the DISTINCT ON form
    // fetchReferencePrices now owns.
    const historyMap = await fetchReferencePrices(skins.map((s) => s.id))

    const topMovers = skins
      .map((skin) => {
        const current = skin.price?.lowestPrice ?? null
        const ref = historyMap.get(skin.id)
        const prev = ref?.price ?? null
        // The move is measured on the reference's basis; the price shown is today's.
        const basis = ref && skin.price ? comparablePrice(skin.price, ref) : null
        const priceChange24h = percentChange(basis, prev)
        return {
          skinId: skin.id,
          marketHashName: skin.marketHashName,
          name: skin.name,
          iconUrl: skin.iconUrl,
          rarity: skin.rarity,
          skinportPrice:   skin.price?.skinportPrice   ?? null,
          csgoMarketPrice: skin.price?.csgoMarketPrice ?? null,
          waxpeerPrice:    skin.price?.waxpeerPrice    ?? null,
          csfloatPrice:    skin.price?.csfloatPrice    ?? null,
          lowestPrice: current,
          priceChange24h,
          volume24h: skin.price?.volume24h ?? null,
          updatedAt: skin.price?.updatedAt.toISOString() ?? new Date().toISOString(),
          _prev: prev,
          _basis: basis,
        }
      })
      // Quality filter: need a real 24h-ago reference price and a move big
      // enough to be worth acting on. Without `_prev` we can't tell noise from
      // a real move; below MIN_TOP_MOVER_ABS_MOVE there is nothing to act on
      // even when the percentage is dramatic.
      .filter(
        (s) =>
          s._prev !== null &&
          s._basis !== null &&
          Math.abs(s._basis - s._prev) >= MIN_TOP_MOVER_ABS_MOVE,
      )
      // Corroboration, part two. The SQL above guaranteed a second quote exists;
      // this asks whether the quotes actually agree. They routinely don't: the
      // same skin listed at $373 and $2 762 across two marketplaces has no
      // meaningful "price", so its 24h change is not a fact worth ranking.
      .filter((s) => isCorroborated(s))
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
