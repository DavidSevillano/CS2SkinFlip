/**
 * Throwaway analysis: how much of the price data is garbage, and is there a
 * real arbitrage signal underneath it?
 *
 * READ-ONLY. Touches production Postgres with SELECTs only, plus three public
 * bulk HTTP endpoints. Writes nothing, anywhere.
 *
 * Run from backend/:  npx tsx scripts/measure-price-quality.ts
 *
 * Answers, in order:
 *   Q1  How far do marketplace prices drift from the Steam reference?
 *   Q2  How much of the current Suben/Bajan list is explained by an outlier?
 *   Q3  What does flipping Skinport's `tradable` flag do to lowestPrice?
 *   Q4  How many real opportunities survive cleaning + fees?
 */
import 'dotenv/config'
import axios from 'axios'
import zlib from 'zlib'
import { promisify } from 'util'
import { PrismaClient } from '@prisma/client'

const brotliDecompress = promisify(zlib.brotliDecompress)
const prisma = new PrismaClient()

// ─── Assumptions to confirm ──────────────────────────────────────────────────
// Seller-side commission per marketplace. THESE ARE PLACEHOLDERS — the Q4
// numbers move a lot with them, so replace with the real current rates before
// treating the opportunity count as decision-grade.
const SELLER_FEES: Record<Marketplace, number> = {
  skinport: 0.12,
  csgoMarket: 0.07,
  waxpeer: 0.06,
}

type Marketplace = 'skinport' | 'csgoMarket' | 'waxpeer'
const MARKETPLACES: Marketplace[] = ['skinport', 'csgoMarket', 'waxpeer']

// ─── Sources ─────────────────────────────────────────────────────────────────

const CSGOTRADER_PRICES_URL = 'https://prices.csgotrader.app/latest/prices.json'

interface CSGOTraderPrices {
  [marketHashName: string]: {
    steam?: { last_24h?: number; last_7d?: number; last_30d?: number }
  }
}

/**
 * Steam reference price per market_hash_name. Prefers the shorter window.
 *
 * As of this run the endpoint answers 200 with the csgotrader SPA's HTML, not
 * JSON — so this returns empty. Kept (and made loud) because populateSkins.ts
 * calls the same URL and swallows the failure silently.
 */
async function fetchSteamReference(): Promise<Map<string, number>> {
  const map = new Map<string, number>()
  const res = await axios.get<CSGOTraderPrices | string>(CSGOTRADER_PRICES_URL, {
    timeout: 60000,
    validateStatus: () => true,
  })
  if (typeof res.data === 'string' || res.status !== 200) {
    console.log(
      `  !! csgotrader returned ${res.status} ${res.headers['content-type']} — not JSON. ` +
        `No Steam reference available.`,
    )
    return map
  }
  for (const [name, entry] of Object.entries(res.data ?? {})) {
    const price = entry?.steam?.last_24h ?? entry?.steam?.last_7d ?? entry?.steam?.last_30d
    if (typeof price === 'number' && price > 0) map.set(name, price)
  }
  return map
}

// ─── Consensus (no external reference needed) ────────────────────────────────
// With no Steam anchor, the marketplaces have to police each other. Three
// independent quotes for the same market_hash_name: if one sits far from the
// median of the others, it is the liar. Two quotes that disagree wildly are
// unusable — nothing says which of them is wrong.

/** Ratio of each price to the median of all available prices for that skin. */
function consensusRatios(prices: number[]): { median: number; ratios: number[] } {
  const sorted = [...prices].sort((a, b) => a - b)
  const median =
    sorted.length % 2 === 1
      ? sorted[(sorted.length - 1) / 2]
      : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
  return { median, ratios: prices.map((p) => p / median) }
}

/**
 * Same call the job makes, but with `tradable` parameterised so we can diff the
 * two. Returns quantity alongside the price: it rides in the same payload the
 * real job already downloads and throws away, and it's our only depth signal.
 */
async function fetchSkinport(
  tradable: 0 | 1,
): Promise<{ prices: Map<string, number>; quantities: Map<string, number> }> {
  const prices = new Map<string, number>()
  const quantities = new Map<string, number>()
  const { data } = await axios.get<Buffer>('https://api.skinport.com/v1/items', {
    params: { app_id: 730, currency: 'USD', tradable },
    timeout: 60000,
    headers: { 'Accept-Encoding': 'br' },
    responseType: 'arraybuffer',
    decompress: false,
  })
  const items: Array<{ market_hash_name: string; min_price: number | null; quantity?: number }> =
    JSON.parse((await brotliDecompress(Buffer.from(data))).toString('utf8'))
  for (const item of items ?? []) {
    if (!item.market_hash_name) continue
    if (typeof item.min_price === 'number' && item.min_price > 0) {
      prices.set(item.market_hash_name, item.min_price)
    }
    if (typeof item.quantity === 'number') {
      quantities.set(item.market_hash_name, item.quantity)
    }
  }
  return { prices, quantities }
}

// ─── Stats helpers ───────────────────────────────────────────────────────────

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return NaN
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.floor((p / 100) * sorted.length)))
  return sorted[idx]
}

function describe(label: string, values: number[]): void {
  const sorted = [...values].sort((a, b) => a - b)
  const fmt = (n: number) => (Number.isFinite(n) ? n.toFixed(3) : 'n/a')
  console.log(
    `  ${label.padEnd(14)} n=${String(sorted.length).padStart(6)}  ` +
      `p1=${fmt(percentile(sorted, 1))}  p10=${fmt(percentile(sorted, 10))}  ` +
      `p50=${fmt(percentile(sorted, 50))}  p90=${fmt(percentile(sorted, 90))}  ` +
      `p99=${fmt(percentile(sorted, 99))}  max=${fmt(sorted[sorted.length - 1])}`,
  )
}

function usd(n: number | null): string {
  return n === null ? '—' : `$${n.toFixed(2)}`
}

// ─── Main ────────────────────────────────────────────────────────────────────

async function main() {
  console.log('Fetching reference data (3 bulk endpoints)...\n')
  const [steamRef, sp0, sp1] = await Promise.all([
    fetchSteamReference(),
    fetchSkinport(0),
    fetchSkinport(1),
  ])
  const skinportTradable0 = sp0.prices
  const skinportTradable1 = sp1.prices
  const quantities = sp1.quantities
  console.log(`  Steam reference (csgotrader): ${steamRef.size} items`)
  console.log(`  Skinport tradable=0:          ${skinportTradable0.size} items`)
  console.log(`  Skinport tradable=1:          ${skinportTradable1.size} items`)

  const rows = await prisma.skinPrice.findMany({
    select: {
      skinId: true,
      skinportPrice: true,
      csgoMarketPrice: true,
      waxpeerPrice: true,
      lowestPrice: true,
      priceChange24h: true,
      skin: { select: { marketHashName: true, name: true } },
    },
  })
  console.log(`  SkinPrice rows in DB:         ${rows.length}\n`)

  type Row = (typeof rows)[number]
  const priceOf = (r: Row, m: Marketplace): number | null =>
    m === 'skinport' ? r.skinportPrice : m === 'csgoMarket' ? r.csgoMarketPrice : r.waxpeerPrice

  // ── Q1: how much do the marketplaces disagree with each other? ────────────
  console.log('─'.repeat(78))
  console.log('Q1  Cross-marketplace disagreement (no external reference is available)')
  console.log('    Coverage first: outlier rejection by consensus needs 3 quotes to work,')
  console.log('    and can only flag-but-not-resolve with 2.')
  console.log('─'.repeat(78))

  const quotesFor = (r: Row) =>
    MARKETPLACES.map((m) => ({ m, p: priceOf(r, m) })).filter(
      (q): q is { m: Marketplace; p: number } => q.p !== null && q.p > 0,
    )

  const byCount = [0, 0, 0, 0]
  for (const r of rows) byCount[quotesFor(r).length]++
  console.log('')
  for (let n = 1; n <= 3; n++) {
    console.log(
      `  ${n} marketplace${n > 1 ? 's' : ''} quoting: ${String(byCount[n]).padStart(6)} rows ` +
        `(${((byCount[n] / rows.length) * 100).toFixed(1)}%)`,
    )
  }

  // Spread between the cheapest and dearest quote. On a healthy item this is a
  // few percent of fee/liquidity difference; a ratio in the hundreds is a lie.
  const disagree2: number[] = []
  const disagree3: number[] = []
  for (const r of rows) {
    const q = quotesFor(r)
    if (q.length < 2) continue
    const ratio = Math.max(...q.map((x) => x.p)) / Math.min(...q.map((x) => x.p))
    ;(q.length === 3 ? disagree3 : disagree2).push(ratio)
  }
  console.log('\n  max/min ratio among the available quotes:')
  describe('3 quotes', disagree3)
  describe('2 quotes', disagree2)

  // With 3 quotes the median identifies the culprit. How often is there one?
  console.log('\n  Rows with 3 quotes where one price is far from the median of the three:')
  for (const threshold of [2, 3, 5, 10, 50]) {
    let flagged = 0
    for (const r of rows) {
      const q = quotesFor(r)
      if (q.length !== 3) continue
      const { ratios } = consensusRatios(q.map((x) => x.p))
      if (ratios.some((x) => x > threshold || x < 1 / threshold)) flagged++
    }
    console.log(
      `    >${threshold}x or <1/${threshold}x off median:  ${String(flagged).padStart(5)} rows ` +
        `(${((flagged / Math.max(byCount[3], 1)) * 100).toFixed(1)}% of 3-quote rows)`,
    )
  }

  // The pathology the user reported, isolated.
  const absurd = rows
    .filter((r) => {
      const q = quotesFor(r)
      if (q.length !== 3) return false
      const { ratios } = consensusRatios(q.map((x) => x.p))
      return ratios.some((x) => x > 10)
    })
    .sort((a, b) => (b.lowestPrice ?? 0) - (a.lowestPrice ?? 0))

  console.log(`\n  Rows where one marketplace asks >10x the median of the three: ${absurd.length}`)
  for (const r of absurd.slice(0, 12)) {
    console.log(
      `    ${r.skin.name.slice(0, 40).padEnd(40)} ` +
        `sp=${usd(r.skinportPrice).padStart(11)}  csgo=${usd(r.csgoMarketPrice).padStart(11)}  ` +
        `wax=${usd(r.waxpeerPrice).padStart(11)}`,
    )
  }

  // ── Q2: how much of the current top-movers list is noise? ─────────────────
  console.log('\n' + '─'.repeat(78))
  console.log('Q2  Current Suben/Bajan lists — how many entries involve an outlier price?')
  console.log('─'.repeat(78))

  for (const direction of ['rising', 'falling'] as const) {
    const sorted = rows
      .filter((r) => r.priceChange24h !== null && (r.lowestPrice ?? 0) >= 1)
      .sort((a, b) =>
        direction === 'rising'
          ? (b.priceChange24h ?? 0) - (a.priceChange24h ?? 0)
          : (a.priceChange24h ?? 0) - (b.priceChange24h ?? 0),
      )
      .slice(0, 20)

    let suspect = 0
    console.log(`\n  Top 20 ${direction}:`)
    for (const r of sorted) {
      const q = quotesFor(r)
      // Flag when the quotes for this skin can't agree within 3x — whatever the
      // 24h change says, it is computed on a number nobody else corroborates.
      const disagreement =
        q.length >= 2 ? Math.max(...q.map((x) => x.p)) / Math.min(...q.map((x) => x.p)) : null
      const flag = q.length < 2 || (disagreement !== null && disagreement > 3)
      if (flag) suspect++
      console.log(
        `    ${flag ? '! ' : '  '}${r.skin.name.slice(0, 38).padEnd(38)} ` +
          `${(r.priceChange24h ?? 0).toFixed(1).padStart(10)}%  low=${usd(r.lowestPrice).padStart(11)}  ` +
          `quotes=${q.length}  spread=${disagreement ? disagreement.toFixed(1) + 'x' : 'n/a'}`,
      )
    }
    console.log(`    -> ${suspect}/20 unverifiable (single quote, or quotes disagreeing >3x)`)
  }

  // ── Q3: impact of the tradable flag ───────────────────────────────────────
  console.log('\n' + '─'.repeat(78))
  console.log('Q3  Skinport tradable=0 (current) vs tradable=1 — effect on lowestPrice')
  console.log('─'.repeat(78))

  const deltas: number[] = []
  let lowestWouldChange = 0
  let skinportLosesTheMin = 0

  for (const r of rows) {
    const p0 = skinportTradable0.get(r.skin.marketHashName)
    const p1 = skinportTradable1.get(r.skin.marketHashName)
    if (p0 === undefined || p1 === undefined) continue
    deltas.push(p1 / p0)

    const others = [r.csgoMarketPrice, r.waxpeerPrice].filter((p): p is number => p !== null && p > 0)
    const lowBefore = Math.min(p0, ...(others.length ? others : [Infinity]))
    const lowAfter = Math.min(p1, ...(others.length ? others : [Infinity]))
    if (Number.isFinite(lowBefore) && Math.abs(lowAfter - lowBefore) > 0.01) lowestWouldChange++
    if (others.length && p0 < Math.min(...others) && p1 > Math.min(...others)) skinportLosesTheMin++
  }

  console.log('\n  Price ratio tradable=1 / tradable=0 (>1 means trade-locked listings were dragging it down):')
  describe('skinport', deltas)
  console.log(`\n  Skins whose lowestPrice would move:            ${lowestWouldChange}`)
  console.log(`  Skins where Skinport stops being the cheapest: ${skinportLosesTheMin}`)
  console.log('\n  NOTE: the first run after this change writes a step into PriceHistory,')
  console.log('        which will show up as one day of fake movement in top-movers.')

  // ── Q4: surviving opportunities ───────────────────────────────────────────
  console.log('\n' + '─'.repeat(78))
  console.log('Q4  Net arbitrage opportunities after outlier rejection and fees')
  console.log(`    Fees applied (PLACEHOLDERS — confirm before trusting): ` +
    MARKETPLACES.map((m) => `${m} ${(SELLER_FEES[m] * 100).toFixed(0)}%`).join(', '))
  console.log('─'.repeat(78))

  interface Opportunity {
    name: string
    buyOn: Marketplace
    buyAt: number
    sellOn: Marketplace
    sellAt: number
    netProfit: number
    netPct: number
    depth: number | null
  }

  /**
   * `maxDeviation` is the consensus rule: with 3 quotes, drop any that sits
   * further than this from the median of the three. With only 2 quotes there is
   * no majority to appeal to, so a disagreement beyond the same factor
   * disqualifies the row outright rather than picking a winner.
   */
  function opportunitiesWith(maxDeviation: number | null, minDepth: number): Opportunity[] {
    const out: Opportunity[] = []
    for (const r of rows) {
      const depth = quantities.get(r.skin.marketHashName) ?? null
      let usable = quotesFor(r)
      if (usable.length < 2) continue

      if (maxDeviation !== null) {
        if (usable.length === 3) {
          const { ratios } = consensusRatios(usable.map((x) => x.p))
          usable = usable.filter((_, i) => ratios[i] <= maxDeviation && ratios[i] >= 1 / maxDeviation)
        }
        if (usable.length < 2) continue
        const spread = Math.max(...usable.map((x) => x.p)) / Math.min(...usable.map((x) => x.p))
        if (spread > maxDeviation) continue
      }

      if (usable.length < 2) continue
      if (minDepth > 0 && (depth === null || depth < minDepth)) continue

      const cheapest = usable.reduce((a, b) => (b.p < a.p ? b : a))
      const dearest = usable.reduce((a, b) => (b.p > a.p ? b : a))
      if (cheapest.m === dearest.m) continue

      const netProceeds = dearest.p * (1 - SELLER_FEES[dearest.m])
      const netProfit = netProceeds - cheapest.p
      if (netProfit <= 0) continue

      out.push({
        name: r.skin.name,
        buyOn: cheapest.m,
        buyAt: cheapest.p,
        sellOn: dearest.m,
        sellAt: dearest.p,
        netProfit,
        netPct: (netProfit / cheapest.p) * 100,
        depth,
      })
    }
    return out.sort((a, b) => b.netProfit - a.netProfit)
  }

  const scenarios: Array<[string, number | null, number]> = [
    ['no cleaning at all', null, 0],
    ['consensus 5x', 5, 0],
    ['consensus 3x', 3, 0],
    ['consensus 2x', 2, 0],
    ['consensus 2x + depth>=3', 2, 3],
    ['consensus 2x + depth>=10', 2, 10],
    ['consensus 1.5x + depth>=10', 1.5, 10],
  ]

  for (const [label, maxDev, minDepth] of scenarios) {
    const opps = opportunitiesWith(maxDev, minDepth)
    const over1 = opps.filter((o) => o.netProfit >= 1).length
    const over5 = opps.filter((o) => o.netProfit >= 5).length
    const over20 = opps.filter((o) => o.netProfit >= 20).length
    console.log(
      `\n  ${label.padEnd(28)} total=${String(opps.length).padStart(5)}  ` +
        `>=$1: ${String(over1).padStart(5)}  >=$5: ${String(over5).padStart(5)}  >=$20: ${String(over20).padStart(5)}`,
    )
  }

  console.log('\n  Top 15 under the strictest scenario (consensus 1.5x + depth>=10):')
  for (const o of opportunitiesWith(1.5, 10).slice(0, 15)) {
    console.log(
      `    ${o.name.slice(0, 38).padEnd(38)} buy ${o.buyOn.padEnd(10)} ${usd(o.buyAt).padStart(10)}` +
        ` → sell ${o.sellOn.padEnd(10)} ${usd(o.sellAt).padStart(10)}` +
        `  net ${usd(o.netProfit).padStart(9)} (${o.netPct.toFixed(1)}%)  depth=${o.depth ?? '?'}`,
    )
  }

  console.log('\nDone. Nothing was written.\n')
}

main()
  .catch((err) => {
    console.error(err)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
