// When can a marketplace quote be believed?
//
// The three bulk endpoints each report their own lowest ask. On a liquid item
// those three numbers agree closely. On an item only one marketplace stocks,
// "the lowest ask" is whatever the single listing says — including a seller
// parking a $2 skin at $61 938 — and nothing in the pipeline contradicts it.
//
// Measured against production on 2026-07-24 with
// `scripts/measure-price-quality.ts`, over 13 537 priced skins:
//
//   quotes per skin    3 -> 58.0%    2 -> 38.3%    1 -> 3.7%
//   max/min ratio among quotes, 3-quote rows:
//     p50 1.135   p90 1.500   p99 12.0   max 105 952
//
// So a healthy item's quotes sit within ~15% of each other, p90 is 1.5x, and
// everything past that is not a market — it is one absurd listing. The 3.7% of
// skins with a single quote produced 15 of the top 20 entries in the Rising
// tab: unverifiable prices are massively over-represented at the extremes,
// which is exactly what you would expect if they are noise.

/**
 * How far a quote may sit from the median of three before it is discarded when
 * computing `lowestPrice`.
 *
 * Applies only when all three marketplaces quote the skin. With two there is no
 * majority to appeal to and no way to tell which of the pair is lying, so both
 * are kept and the row is instead held to `MAX_TOP_MOVER_QUOTE_SPREAD` wherever
 * that matters.
 *
 * 4x sits far above the p90 of genuine spread (1.5x) and well below the p99 of
 * noise (12x), so it removes clear lies without touching real price dispersion.
 */
export const MAX_QUOTE_DEVIATION_FROM_MEDIAN = 4

// The highest-value rule of the three has no constant here on purpose: top
// movers requires at least two marketplaces to quote a skin, and that is
// enforced structurally in `services/prices.ts` as an OR over the three column
// pairs, because Prisma has no counting operator over sibling columns. A `2`
// exported from this file would be a number nobody reads — change it and
// nothing happens. It costs 3.7% of the catalog and removed the majority of the
// nonsense from the home screen.

/**
 * Cheapest skin, and smallest absolute 24h move, that top movers will rank.
 *
 * Ranking by percentage rewards cheap skins for nothing: a $1.20 item moving
 * $0.57 is +90% and outranks a $336 knife moving $216 at +182%. Six of the top
 * twenty risers were sub-$5 skins whose entire "movement" was under a dollar —
 * correct numbers answering a question nobody asked, since no marketplace makes
 * a $0.39 move tradeable after fees.
 *
 * Measured 2026-07-24 with `scripts/measure-mover-thresholds.ts`, which sweeps
 * the grid and checks the list still fills. Every combination tried filled
 * 20/20 — the catalog is far larger than the list, so starvation is not the
 * constraint and the choice is purely about who the screen is for. At $5/$2 the
 * cheapest entry moves from $1.20 to $7.78 and the median from $34.55 to
 * $92.10, while an $7.78 R8 Revolver still qualifies. The next step up
 * ($10/$5) pushes the cheapest entry to $29.79, which stops being noise removal
 * and starts excluding everything a user with little capital can act on.
 */
export const MIN_TOP_MOVER_PRICE = 5
export const MIN_TOP_MOVER_ABS_MOVE = 2

/**
 * Top movers rejects a skin whose available quotes disagree by more than this
 * factor, whatever its 24h change says.
 *
 * Tighter than `MAX_QUOTE_DEVIATION_FROM_MEDIAN` because the two rules answer
 * different questions. That one asks "which of these three numbers is wrong?"
 * and can act on the answer. This one asks "do I trust this row enough to put
 * it at the top of the app's first screen?", where the honest answer to a 3x
 * disagreement is no, even though something has to be shown on the detail
 * screen for the same skin.
 */
export const MAX_TOP_MOVER_QUOTE_SPREAD = 3
