/**
 * One-off maintenance: compact PriceHistory.
 *
 * The bulk job writes 4 points per skin per day and cleanupPriceHistory deletes
 * 3 of them once they age past the raw band. Half a million deletes a day leave
 * half-empty pages that plain VACUUM marks reusable but never compacts, while
 * new rows keep appending to the end. Measured 2026-07-24: 112.7 bytes of real
 * tuple sitting in a 262-byte heap footprint, and indexes at ~3x their packed
 * size.
 *
 * VACUUM FULL rewrites the table and every index, releasing the slack back to
 * the filesystem. It takes an ACCESS EXCLUSIVE lock for the duration — reads and
 * writes to PriceHistory block until it finishes — so this is not something to
 * run casually. Nothing is deleted: same rows in, same rows out.
 *
 * Uses DIRECT_DATABASE_URL: VACUUM cannot run through a transaction-mode pooler.
 *
 * Run from backend/:  npx tsx scripts/compact-price-history.ts
 */
import 'dotenv/config'
import { PrismaClient } from '@prisma/client'

const url = process.env.DIRECT_DATABASE_URL
if (!url) {
  console.error('DIRECT_DATABASE_URL is required (VACUUM cannot run through the pooler).')
  process.exit(1)
}

const prisma = new PrismaClient({ datasources: { db: { url } } })

interface SizeRow {
  heap: string
  indexes: string
  total: string
  total_bytes: bigint
}

async function sizes(): Promise<SizeRow> {
  const [row] = await prisma.$queryRawUnsafe<SizeRow[]>(
    `SELECT pg_size_pretty(pg_relation_size('"PriceHistory"'))       AS heap,
            pg_size_pretty(pg_indexes_size('"PriceHistory"'))        AS indexes,
            pg_size_pretty(pg_total_relation_size('"PriceHistory"')) AS total,
            pg_total_relation_size('"PriceHistory"')                 AS total_bytes`,
  )
  return row
}

async function main() {
  const [{ count: before }] = await prisma.$queryRawUnsafe<Array<{ count: bigint }>>(
    `SELECT COUNT(*)::bigint AS count FROM "PriceHistory"`,
  )
  const sizeBefore = await sizes()
  console.log(`Before:  ${before} rows   heap ${sizeBefore.heap}, indexes ${sizeBefore.indexes}, total ${sizeBefore.total}`)

  console.log('\nRunning VACUUM FULL (table is locked until this returns)...')
  const started = Date.now()
  await prisma.$executeRawUnsafe(`VACUUM (FULL, ANALYZE) "PriceHistory"`)
  const elapsed = ((Date.now() - started) / 1000).toFixed(1)

  const [{ count: after }] = await prisma.$queryRawUnsafe<Array<{ count: bigint }>>(
    `SELECT COUNT(*)::bigint AS count FROM "PriceHistory"`,
  )
  const sizeAfter = await sizes()

  console.log(`\nAfter:   ${after} rows   heap ${sizeAfter.heap}, indexes ${sizeAfter.indexes}, total ${sizeAfter.total}`)
  console.log(`Took ${elapsed}s`)

  const reclaimed = Number(sizeBefore.total_bytes) - Number(sizeAfter.total_bytes)
  console.log(`Reclaimed ${(reclaimed / 1024 / 1024).toFixed(1)} MB`)

  if (before !== after) {
    console.error(`\n!! Row count changed (${before} -> ${after}). VACUUM FULL must not lose rows — investigate.`)
    process.exitCode = 1
  }
}

main()
  .catch((err) => {
    console.error(err)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
