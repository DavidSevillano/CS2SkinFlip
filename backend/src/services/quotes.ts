import { MAX_TOP_MOVER_QUOTE_SPREAD } from '../config/priceQuality'

export interface MarketplaceQuotes {
  skinportPrice: number | null
  csgoMarketPrice: number | null
  waxpeerPrice: number | null
}

/**
 * How far apart the marketplaces are on the same skin, as a ratio of dearest to
 * cheapest. 1 means they agree exactly; fewer than two quotes means the question
 * doesn't apply, and Infinity keeps such a row out of anything that filters on
 * agreement rather than letting it through on a technicality.
 */
export function quoteSpread(prices: MarketplaceQuotes): number {
  const quotes = [prices.skinportPrice, prices.csgoMarketPrice, prices.waxpeerPrice].filter(
    (p): p is number => p !== null && p > 0,
  )
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
