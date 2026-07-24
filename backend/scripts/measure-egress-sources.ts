/**
 * Which queries are actually shipping rows out of the database?
 *
 * READ-ONLY. Reads the statistics views only — no application tables.
 *
 * Neon's "network transfer" allowance is consumed by bytes leaving Postgres, so
 * the question is which statements return the most rows, not which run most
 * often. `pg_stat_statements` answers it directly if the extension is present;
 * `pg_stat_user_tables` gives a coarser per-table fallback that is always there.
 *
 * Run from backend/:  npx tsx scripts/measure-egress-sources.ts
 */
import 'dotenv/config'
import { PrismaClient } from '@prisma/client'

const prisma = new PrismaClient()

function n(v: unknown): string {
  return typeof v === 'bigint' || typeof v === 'number' ? Number(v).toLocaleString() : String(v)
}

async function hasPgStatStatements(): Promise<boolean> {
  const rows = await prisma.$queryRawUnsafe<Array<{ exists: boolean }>>(
    `SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements') AS exists`,
  )
  return rows[0]?.exists === true
}

async function topStatements() {
  interface Row {
    rows: bigint
    calls: bigint
    rows_per_call: number
    query: string
  }
  const rows = await prisma.$queryRawUnsafe<Row[]>(
    `SELECT s.rows,
            s.calls,
            (s.rows::numeric / GREATEST(s.calls, 1))::numeric(12,1) AS rows_per_call,
            s.query
     FROM pg_stat_statements s
     WHERE s.query NOT ILIKE '%pg_stat%'
     ORDER BY s.rows DESC
     LIMIT 15`,
  )

  console.log('─'.repeat(100))
  console.log('Statements by total rows returned (this is what egress is made of)')
  console.log('─'.repeat(100))
  for (const r of rows) {
    console.log(
      `\n  rows=${n(r.rows).padStart(12)}  calls=${n(r.calls).padStart(8)}  rows/call=${n(r.rows_per_call).padStart(9)}`,
    )
    console.log(`    ${r.query.replace(/\s+/g, ' ').slice(0, 200)}`)
  }
}

async function perTable() {
  interface Row {
    relname: string
    seq_scan: bigint
    seq_tup_read: bigint
    idx_scan: bigint | null
    idx_tup_fetch: bigint | null
    total_read: bigint
  }
  const rows = await prisma.$queryRawUnsafe<Row[]>(
    `SELECT relname,
            seq_scan,
            seq_tup_read,
            idx_scan,
            idx_tup_fetch,
            (seq_tup_read + COALESCE(idx_tup_fetch, 0)) AS total_read
     FROM pg_stat_user_tables
     ORDER BY total_read DESC`,
  )

  console.log('\n' + '─'.repeat(100))
  console.log('Tuples read per table since stats were last reset')
  console.log('  (rows READ, not rows returned to the client — a sequential scan reads far more')
  console.log('   than it returns, so treat this as "where the work is", not egress itself)')
  console.log('─'.repeat(100))
  for (const r of rows) {
    console.log(
      `  ${r.relname.padEnd(20)} total_read=${n(r.total_read).padStart(14)}   ` +
        `seq_scans=${n(r.seq_scan).padStart(8)} (${n(r.seq_tup_read)} tuples)   ` +
        `idx_fetch=${n(r.idx_tup_fetch ?? 0).padStart(12)}`,
    )
  }

  const [reset] = await prisma.$queryRawUnsafe<Array<{ stats_reset: Date | null }>>(
    `SELECT stats_reset FROM pg_stat_database WHERE datname = current_database()`,
  )
  console.log(`\n  Stats window began: ${reset?.stats_reset?.toISOString() ?? 'unknown'}`)
}

async function main() {
  if (await hasPgStatStatements()) {
    await topStatements()
  } else {
    console.log('pg_stat_statements is not installed — no per-statement attribution available.')
    console.log('Enable it on Neon with:  CREATE EXTENSION pg_stat_statements;')
    console.log('It only records from that point on, so it answers next week, not today.\n')
  }

  await perTable()
  console.log('\nDone. Nothing was written.\n')
}

main()
  .catch((err) => {
    console.error(err)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
