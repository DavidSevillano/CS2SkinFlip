/**
 * Sweeps a minimum-listings threshold over what top movers would rank today,
 * using CS:GO Market's public `volume` (its listing count) as the liquidity
 * signal — the same number `populatePrices` stores in `SkinPrice.volume24h`.
 *
 * Joins the live feed in-process rather than reading `volume24h`, so it works
 * before the first run that fills the column.
 *
 * READ-ONLY. Production cost: the same candidate query and reference lookup a
 * top-movers request makes, twice — a few hundred rows.
 *
 * Run from backend/:  npx tsx scripts/measure-mover-liquidity.ts
 */
import 'dotenv/config'
import axios from 'axios'
import { prisma } from '../src/db/prisma'
import { fetchReferencePrices } from '../src/services/priceReference'
import { isCorroborated } from '../src/services/quotes'
import { MIN_TOP_MOVER_ABS_MOVE, MIN_TOP_MOVER_PRICE } from '../src/config/priceQuality'

const THRESHOLDS = [0, 3, 5, 8, 10, 15, 20, 30]
const LIMIT = 20
const CANDIDATES = Number(process.env.CANDIDATES ?? 1000)
// Prints the list as it would look at this threshold (no-volume rows kept).
const SHOW_MIN = Number(process.env.SHOW_MIN ?? 0)

async function main() {
  const feed = await axios.get<{ items: Array<{ market_hash_name: string; volume: string | number }> }>(
    'https://market.csgo.com/api/v2/prices/USD.json',
    { timeout: 30000 },
  )
  const volumes = new Map(feed.data.items.map((i) => [i.market_hash_name, Number(i.volume)]))

  for (const direction of ['rising', 'falling'] as const) {
    const sortOrder = direction === 'falling' ? 'asc' : 'desc'
    const skins = await prisma.skin.findMany({
      select: {
        id: true,
        marketHashName: true,
        price: { select: { lowestPrice: true, skinportPrice: true, csgoMarketPrice: true, waxpeerPrice: true } },
      },
      where: {
        price: {
          lowestPrice: { gte: MIN_TOP_MOVER_PRICE },
          OR: [
            { skinportPrice: { not: null }, csgoMarketPrice: { not: null } },
            { skinportPrice: { not: null }, waxpeerPrice: { not: null } },
            { csgoMarketPrice: { not: null }, waxpeerPrice: { not: null } },
          ],
        },
      },
      orderBy: [{ price: { priceChange24h: { sort: sortOrder, nulls: 'last' } } }, { price: { lowestPrice: 'desc' } }],
      take: CANDIDATES,
    })
    const refs = await fetchReferencePrices(skins.map((s) => s.id))

    const ranked = skins
      .map((s) => {
        const current = s.price!.lowestPrice!
        const prev = refs.get(s.id) ?? null
        return {
          name: s.marketHashName,
          current,
          change: prev ? ((current - prev) / prev) * 100 : null,
          absMove: prev ? Math.abs(current - prev) : 0,
          corroborated: isCorroborated(s.price!),
          volume: volumes.get(s.marketHashName) ?? null,
        }
      })
      .filter((r) => r.change !== null && r.absMove >= MIN_TOP_MOVER_ABS_MOVE && r.corroborated)
      .sort((a, b) => (direction === 'falling' ? a.change! - b.change! : b.change! - a.change!))

    console.log(`\n${direction.toUpperCase()} — ${ranked.length} qualifying of ${CANDIDATES} candidates`)
    for (const r of ranked.filter((x) => x.volume === null || x.volume >= SHOW_MIN).slice(0, LIMIT)) {
      console.log(
        `  ${r.name.slice(0, 44).padEnd(44)} ${r.change!.toFixed(1).padStart(8)}%  $${r.current.toFixed(2).padStart(9)}  listings=${r.volume ?? '—'}`,
      )
    }
    for (const t of THRESHOLDS) {
      const keepNull = ranked.filter((r) => r.volume === null || r.volume >= t)
      const dropNull = ranked.filter((r) => r.volume !== null && r.volume >= t)
      const top = keepNull.slice(0, LIMIT)
      const median = top.length ? top[Math.floor(top.length / 2)].change!.toFixed(1) : '—'
      console.log(
        `  min ${String(t).padStart(2)} listings: fills ${String(Math.min(keepNull.length, LIMIT)).padStart(2)}/20 (no-volume kept) ` +
          `${String(Math.min(dropNull.length, LIMIT)).padStart(2)}/20 (no-volume dropped)  top=${top[0]?.change?.toFixed(1) ?? '—'}%  median=${median}%`,
      )
    }
  }
}

main()
  .catch((err) => {
    console.error(err)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
