/**
 * Shows what the home screen would render, by calling the real
 * PriceService.getTopMovers against production.
 *
 * READ-ONLY on Postgres. It does populate the normal `top-movers:*` Redis keys
 * (15-min TTL) exactly as a user request would — the route would write the same
 * values.
 *
 * Note the DB's `lowestPrice` column is still whatever the last bulk run wrote,
 * i.e. a plain MIN. So this previews the corroboration filters only; the
 * outlier rejection inside calcLowestPrice lands on the next cron run.
 *
 * Run from backend/:  npx tsx scripts/preview-top-movers.ts
 */
import 'dotenv/config'
import { PriceService, invalidateTopMoversCache } from '../src/services/prices'
import { prisma } from '../src/db/prisma'

function usd(n: number | null): string {
  return n === null ? '—' : `$${n.toFixed(2)}`
}

async function main() {
  // Otherwise we would just read back whatever the old logic cached.
  const dropped = await invalidateTopMoversCache()
  console.log(`Cleared ${dropped} cached top-movers key(s).\n`)

  const service = new PriceService()

  for (const direction of ['rising', 'falling'] as const) {
    const movers = await service.getTopMovers(direction, 20)
    console.log('─'.repeat(94))
    console.log(`${direction.toUpperCase()} — ${movers.length} entries`)
    console.log('─'.repeat(94))
    for (const [i, m] of movers.entries()) {
      const quotes = [m.skinportPrice, m.csgoMarketPrice, m.waxpeerPrice].filter(
        (p): p is number => p !== null && p > 0,
      )
      const spread = quotes.length >= 2 ? Math.max(...quotes) / Math.min(...quotes) : Infinity
      console.log(
        `  ${String(i + 1).padStart(2)}. ${m.name.slice(0, 38).padEnd(38)} ` +
          `${(m.priceChange24h ?? 0).toFixed(1).padStart(8)}%  ` +
          `${usd(m.lowestPrice).padStart(10)}  quotes=${quotes.length}  spread=${spread.toFixed(2)}x`,
      )
    }
    console.log('')
  }
}

main()
  .catch((err) => {
    console.error(err)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
