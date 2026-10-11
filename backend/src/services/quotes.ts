import {
  CSFLOAT_IN_LOWEST_FROM,
  MAX_QUOTE_DEVIATION_FROM_MEDIAN,
  MAX_TOP_MOVER_QUOTE_SPREAD,
} from '../config/priceQuality'

export interface MarketplaceQuotes {
  skinportPrice: number | null
  csgoMarketPrice: number | null
  waxpeerPrice: number | null
  csfloatPrice: number | null
}

function quotesOf(prices: MarketplaceQuotes): number[] {
  return [prices.skinportPrice, prices.csgoMarketPrice, prices.waxpeerPrice, prices.csfloatPrice].filter(
    (p): p is number => p !== null && p > 0,
  )
}

/**
 * How far apart the marketplaces are on the same skin, as a ratio of dearest to
 * cheapest. 1 means they agree exactly; fewer than two quotes means the question
 * doesn't apply, and Infinity keeps such a row out of anything that filters on
 * agreement rather than letting it through on a technicality.
 */
export function quoteSpread(prices: MarketplaceQuotes): number {
  const quotes = quotesOf(prices)
  if (quotes.length < 2) return Infinity
  return Math.max(...quotes) / Math.min(...quotes)
}

/**
 * At least two marketplaces quote the skin and they agree within
 * MAX_TOP_MOVER_QUOTE_SPREAD — a price someone can check, not one listing.
 * What top movers ranks on and what an alert is allowed to fire on.
 */
export function isCorroborated(prices: MarketplaceQuotes): boolean {
  return quoteSpread(prices) <= MAX_TOP_MOVER_QUOTE_SPREAD
}

/**
 * The cheapest price we are willing to believe, which is not the same as the
 * cheapest price we were quoted.
 *
 * A plain MIN trusts whichever marketplace is most wrong. With three or more
 * quotes the median is the majority opinion, so a quote far from it is an
 * outlier rather than a bargain, and dropping it costs nothing real — see
 * `config/priceQuality.ts` for the measured distribution behind the threshold.
 *
 * With fewer than three quotes there is no majority to appeal to: two numbers
 * that disagree cannot say which of them is lying. Those rows keep the plain
 * MIN and are held to a stricter standard by the readers that care — top movers
 * refuses them outright rather than pretending the price is verified.
 */
export function calcLowestPrice(...prices: (number | null | undefined)[]): number | null {
  const valid = prices.filter((p): p is number => p != null && p > 0)
  if (valid.length === 0) return null
  if (valid.length < 3) return Math.min(...valid)

  const sorted = [...valid].sort((a, b) => a - b)
  const mid = sorted.length / 2
  const median = sorted.length % 2 === 1 ? sorted[Math.floor(mid)] : (sorted[mid - 1] + sorted[mid]) / 2
  // Never empties: with an odd count the median is itself a quote, and with an
  // even one the larger middle quote is at most 2x the median.
  const corroborated = valid.filter(
    (p) =>
      p <= median * MAX_QUOTE_DEVIATION_FROM_MEDIAN &&
      p >= median / MAX_QUOTE_DEVIATION_FROM_MEDIAN,
  )
  return Math.min(...corroborated)
}

/**
 * `lowestPrice` as it is computed at time `at`: CSFloat counts from
 * CSFLOAT_IN_LOWEST_FROM on. Before that it is stored but left out.
 */
export function lowestPriceAt(prices: MarketplaceQuotes, at: number): number | null {
  return at >= CSFLOAT_IN_LOWEST_FROM
    ? calcLowestPrice(prices.skinportPrice, prices.csgoMarketPrice, prices.waxpeerPrice, prices.csfloatPrice)
    : calcLowestPrice(prices.skinportPrice, prices.csgoMarketPrice, prices.waxpeerPrice)
}
