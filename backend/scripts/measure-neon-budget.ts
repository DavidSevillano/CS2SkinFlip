/**
 * What would the proposed price-pipeline changes cost on Neon's free tier?
 *
 * READ-ONLY. Uses COUNT / pg_total_relation_size (a handful of rows back), plus
 * one narrow column read for the skins that currently have no price at all.
 *
 * Run from backend/:  npx tsx scripts/measure-neon-budget.ts
 *
 * Free-tier ceilings: 0.5 GB storage, 5 GB egress per cycle.
 */
import 'dotenv/config'
import axios from 'axios'
import zlib from 'zlib'
import { promisify } from 'util'
import { PrismaClient } from '@prisma/client'

const brotliDecompress = promisify(zlib.brotliDecompress)
const prisma = new PrismaClient()

const RUNS_PER_DAY = 4 // cron '0 */6 * * *'
const RAW_RETENTION_DAYS = 7
const DAILY_RETENTION_DAYS = 90

async function skinportNames(tradable: 0 | 1): Promise<Set<string>> {
  const { data } = await axios.get<Buffer>('https://api.skinport.com/v1/items', {
    params: { app_id: 730, currency: 'USD', tradable },
    timeout: 60000,
    headers: { 'Accept-Encoding': 'br' },
    responseType: 'arraybuffer',
    decompress: false,
  })
  const items: Array<{ market_hash_name: string; min_price: number | null }> = JSON.parse(
    (await brotliDecompress(Buffer.from(data))).toString('utf8'),
  )
  const out = new Set<string>()
  for (const i of items ?? []) {
    if (i.market_hash_name && typeof i.min_price === 'number' && i.min_price > 0) {
      out.add(i.market_hash_name)
    }
  }
  return out
}

function mb(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function main() {
  // ── Where the storage actually sits ──────────────────────────────────────
  const sizes = await prisma.$queryRaw<Array<{ table: string; total: bigint; rows: bigint }>>`
    SELECT
      c.relname                             AS table,
      pg_total_relation_size(c.oid)         AS total,
      c.reltuples::bigint                   AS rows
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind = 'r'
    ORDER BY pg_total_relation_size(c.oid) DESC
  `

  console.log('─'.repeat(72))
  console.log('Storage by table (0.5 GB ceiling)')
  console.log('─'.repeat(72))
  let total = 0
  for (const s of sizes) {
    total += Number(s.total)
    console.log(
      `  ${s.table.padEnd(24)} ${mb(Number(s.total)).padStart(10)}  ~${String(s.rows).padStart(9)} rows`,
    )
  }
  console.log(`  ${'TOTAL'.padEnd(24)} ${mb(total).padStart(10)}`)

  const [{ count: historyRows }] = await prisma.$queryRaw<Array<{ count: bigint }>>`
    SELECT COUNT(*)::bigint AS count FROM "PriceHistory"
  `
  const [{ count: pricedSkins }] = await prisma.$queryRaw<Array<{ count: bigint }>>`
    SELECT COUNT(*)::bigint AS count FROM "SkinPrice"
  `
  const [{ count: catalogSkins }] = await prisma.$queryRaw<Array<{ count: bigint }>>`
    SELECT COUNT(*)::bigint AS count FROM "Skin"
  `

  const historyTable = sizes.find((s) => s.table === 'PriceHistory')
  const bytesPerHistoryRow = historyTable ? Number(historyTable.total) / Number(historyRows) : 0

  console.log(`\n  PriceHistory: ${historyRows} rows, ${bytesPerHistoryRow.toFixed(0)} bytes/row all-in (incl. indexes)`)
  console.log(`  Priced skins: ${pricedSkins} of ${catalogSkins} in catalog`)

  // ── Steady state under current retention ─────────────────────────────────
  // Raw band keeps RUNS_PER_DAY points/skin/day for 7 days, then 1/skin/day to 90.
  const pointsPerSkin = RAW_RETENTION_DAYS * RUNS_PER_DAY + (DAILY_RETENTION_DAYS - RAW_RETENTION_DAYS)
  const steadyRows = Number(pricedSkins) * pointsPerSkin
  console.log(`\n  Steady-state PriceHistory at current retention (7d raw @${RUNS_PER_DAY}/day + 83d daily):`)
  console.log(`    ${pointsPerSkin} points/skin x ${pricedSkins} skins = ${steadyRows.toLocaleString()} rows`)
  console.log(`    ≈ ${mb(steadyRows * bytesPerHistoryRow)} for PriceHistory alone`)
  console.log(`    (currently at ${historyRows} rows — below steady state means it is still filling up)`)

  // ── What tradable=1 would add ────────────────────────────────────────────
  console.log('\n' + '─'.repeat(72))
  console.log('Cost of switching Skinport to tradable=1')
  console.log('─'.repeat(72))

  const [names0, names1] = await Promise.all([skinportNames(0), skinportNames(1)])

  // Catalog skins that have NO price row today — these are the ones that could
  // newly acquire one, and each new priced skin is a permanent PriceHistory
  // stream, not a one-off write.
  const unpriced = await prisma.skin.findMany({
    where: { price: { is: null } },
    select: { marketHashName: true },
  })
  const newlyPriced = unpriced.filter((s) => !names0.has(s.marketHashName) && names1.has(s.marketHashName))

  console.log(`\n  Skinport names with a price: tradable=0 → ${names0.size}, tradable=1 → ${names1.size}`)
  console.log(`  Catalog skins with no price row today: ${unpriced.length}`)
  console.log(`  ...of those, newly priced under tradable=1: ${newlyPriced.length}`)

  const newPricedTotal = Number(pricedSkins) + newlyPriced.length
  const growthPct = (newlyPriced.length / Number(pricedSkins)) * 100
  const newSteadyRows = newPricedTotal * pointsPerSkin

  console.log(`\n  Priced skins: ${pricedSkins} → ${newPricedTotal}  (+${growthPct.toFixed(1)}%)`)
  console.log(`  Steady-state PriceHistory: ${steadyRows.toLocaleString()} → ${newSteadyRows.toLocaleString()} rows`)
  console.log(`    ${mb(steadyRows * bytesPerHistoryRow)} → ${mb(newSteadyRows * bytesPerHistoryRow)}`)
  console.log(`    delta: +${mb((newSteadyRows - steadyRows) * bytesPerHistoryRow)}`)

  // ── Egress per job run ───────────────────────────────────────────────────
  console.log('\n' + '─'.repeat(72))
  console.log('Egress per bulk run (5 GB/cycle ceiling)')
  console.log('─'.repeat(72))
  console.log('  Neon counts bytes leaving the DB. The job reads before it writes:')

  const catalogReadBytes = Number(catalogSkins) * 70 // id + marketHashName, rough
  const historyReadBytes = Number(pricedSkins) * 45 // DISTINCT ON skinId, price
  const perRun = catalogReadBytes + historyReadBytes
  console.log(`    skin.findMany(id, marketHashName): ${catalogSkins} rows ≈ ${mb(catalogReadBytes)}`)
  console.log(`    DISTINCT ON PriceHistory:          ~${pricedSkins} rows ≈ ${mb(historyReadBytes)}`)
  console.log(`    per run ≈ ${mb(perRun)}   x${RUNS_PER_DAY}/day ≈ ${mb(perRun * RUNS_PER_DAY)}/day`)
  console.log(`    ≈ ${mb(perRun * RUNS_PER_DAY * 30)}/month from the job's reads alone`)
  console.log(`\n  Under tradable=1 the DISTINCT ON grows with the priced-skin count:`)
  const perRunAfter = catalogReadBytes + newPricedTotal * 45
  console.log(`    per run ≈ ${mb(perRunAfter)}  →  ${mb(perRunAfter * RUNS_PER_DAY * 30)}/month`)
  console.log(`    delta ≈ +${mb((perRunAfter - perRun) * RUNS_PER_DAY * 30)}/month`)

  console.log('\n  NOTE: this only models the job. Whatever else is consuming the')
  console.log('        4.1 GB (API traffic, Studio, migrations) is not measured here.')
  console.log('\nDone. Nothing was written.\n')
}

main()
  .catch((err) => {
    console.error(err)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
