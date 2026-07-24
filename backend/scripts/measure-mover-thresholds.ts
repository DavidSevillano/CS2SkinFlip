/**
 * What price floor and minimum absolute move should top movers require?
 *
 * READ-ONLY. Replicates getTopMovers' candidate query and filter chain with the
 * thresholds parameterised, so the numbers come from production rather than
 * from taste.
 *
 * The question: ranking by pure percentage rewards cheap skins, because a $1.20
 * item moving $0.57 is +90% while a $336 knife moving $216 is +182%. Both rank,
 * and the $0.57 one is not a trade anybody can make. The floors have to remove
 * that without starving the list — under-filling is its own failure.
 *
 * Run from backend/:  npx tsx scripts/measure-mover-thresholds.ts
 */
import 'dotenv/config'
import { PrismaClient } from '@prisma/client'
import { CHANGE_REFERENCE_WINDOW_MS } from '../src/config/priceHistory'
import { MAX_TOP_MOVER_QUOTE_SPREAD } from '../src/config/priceQuality'

const prisma = new PrismaClient()

const LIMIT = 20
const OVERFETCH = Math.max(LIMIT * 5, 100)

const atLeastTwoQuotes = [
  { skinportPrice: { not: null }, csgoMarketPrice: { not: null } },
  { skinportPrice: { not: null }, waxpeerPrice: { not: null } },
  { csgoMarketPrice: { not: null }, waxpeerPrice: { not: null } },
]

interface Mover {
  name: string
  lowestPrice: number
  prev: number
  pct: number
  absMove: number
}

async function movers(
  direction: 'rising' | 'falling',
  minPrice: number,
  minAbsMove: number,
): Promise<Mover[]> {
  const sortOrder = direction === 'falling' ? 'asc' : 'desc'

  const skins = await prisma.skin.findMany({
    include: { price: true },
    where: { price: { lowestPrice: { gte: minPrice }, OR: atLeastTwoQuotes } },
    orderBy: [
      { price: { priceChange24h: { sort: sortOrder, nulls: 'last' } } },
      { price: { lowestPrice: 'desc' } },
    ],
    take: OVERFETCH,
  })

  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const windowStart = new Date(dayAgo.getTime() - CHANGE_REFERENCE_WINDOW_MS)
  const histories = await prisma.priceHistory.findMany({
    where: {
      skinId: { in: skins.map((s) => s.id) },
      timestamp: { gte: windowStart, lte: dayAgo },
    },
    orderBy: { timestamp: 'desc' },
    distinct: ['skinId'],
  })
  const historyMap = new Map(histories.map((h) => [h.skinId, h.price]))

  return skins
    .flatMap((skin) => {
      const current = skin.price?.lowestPrice ?? null
      const prev = historyMap.get(skin.id) ?? null
      if (current === null || prev === null || prev <= 0) return []

      const quotes = [
        skin.price?.skinportPrice,
        skin.price?.csgoMarketPrice,
        skin.price?.waxpeerPrice,
      ].filter((p): p is number => p != null && p > 0)
      if (quotes.length < 2) return []
      if (Math.max(...quotes) / Math.min(...quotes) > MAX_TOP_MOVER_QUOTE_SPREAD) return []

      const absMove = Math.abs(current - prev)
      if (absMove < minAbsMove) return []

      return [{ name: skin.name, lowestPrice: current, prev, pct: ((current - prev) / prev) * 100, absMove }]
    })
    .sort((a, b) => (direction === 'falling' ? a.pct - b.pct : b.pct - a.pct))
    .slice(0, LIMIT)
}

function median(xs: number[]): number {
  if (xs.length === 0) return NaN
  const s = [...xs].sort((a, b) => a - b)
  return s[Math.floor(s.length / 2)]
}

async function main() {
  const grid: Array<[number, number]> = [
    [1, 0.25], // current behaviour
    [2, 0.5],
    [5, 1],
    [5, 2],
    [10, 2],
    [10, 5],
    [20, 5],
  ]

  console.log('minPrice / minAbsMove  ->  can the list still fill, and what is in it?\n')
  console.log(
    '  floor    move    dir       filled  cheapest   median$   smallest move   top %',
  )
  console.log('  ' + '─'.repeat(88))

  for (const [minPrice, minAbsMove] of grid) {
    for (const direction of ['rising', 'falling'] as const) {
      const list = await movers(direction, minPrice, minAbsMove)
      const prices = list.map((m) => m.lowestPrice)
      const moves = list.map((m) => m.absMove)
      console.log(
        `  $${String(minPrice).padEnd(6)} $${String(minAbsMove).padEnd(6)} ${direction.padEnd(9)} ` +
          `${String(list.length).padStart(4)}/${LIMIT}  ` +
          `${('$' + Math.min(...prices).toFixed(2)).padStart(9)}  ` +
          `${('$' + median(prices).toFixed(2)).padStart(9)}  ` +
          `${('$' + Math.min(...moves).toFixed(2)).padStart(13)}  ` +
          `${(list[0]?.pct ?? 0).toFixed(1).padStart(7)}%`,
      )
    }
  }

  // Eyeball the recommended setting.
  for (const direction of ['rising', 'falling'] as const) {
    console.log(`\n${'─'.repeat(94)}\n${direction.toUpperCase()} at minPrice $5 / minAbsMove $2\n${'─'.repeat(94)}`)
    for (const [i, m] of (await movers(direction, 5, 2)).entries()) {
      console.log(
        `  ${String(i + 1).padStart(2)}. ${m.name.slice(0, 40).padEnd(40)} ` +
          `${m.pct.toFixed(1).padStart(8)}%   $${m.lowestPrice.toFixed(2).padStart(9)}   ` +
          `moved $${m.absMove.toFixed(2)}`,
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
