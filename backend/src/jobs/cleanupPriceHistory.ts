import { prisma } from '../db/prisma'
import type { FastifyBaseLogger } from 'fastify'

// Resolución decreciente:
//   0–14 días   → raw (~12 pts/día, cada 2h)
//   14–120 días → 2 pts/día (precio mínimo y máximo del día)
//   >120 días   → borrado
const RAW_RETENTION_DAYS = 14
const DAILY_RETENTION_DAYS = 120

// El job se invoca cada 2h; el downsample escanea toda la franja 14–120d, así que
// solo hacemos el trabajo real como mucho una vez cada 24h. Guard en memoria: tras
// un reinicio vuelve a correr, lo cual es inofensivo por ser idempotente.
const MIN_INTERVAL_MS = 24 * 60 * 60 * 1000
let lastRunAt = 0

export async function cleanupPriceHistory(log: FastifyBaseLogger) {
  const now = Date.now()
  if (now - lastRunAt < MIN_INTERVAL_MS) {
    log.info('[PriceHistoryCleanup] Skipped — ran less than 24h ago')
    return
  }
  const hardCutoff = new Date(now - DAILY_RETENTION_DAYS * 24 * 60 * 60 * 1000)
  const rawCutoff = new Date(now - RAW_RETENTION_DAYS * 24 * 60 * 60 * 1000)

  // 1) Hard delete de lo más antiguo que la retención diaria (reduce el conjunto).
  const hardDeleted = await prisma.$executeRaw`
    DELETE FROM "PriceHistory" WHERE "timestamp" < ${hardCutoff}
  `

  // 2) Downsample de la franja 14–120d: por (skinId, día UTC) conservar solo las
  //    filas con precio mínimo y máximo; borrar las intermedias. Idempotente.
  const downsampled = await prisma.$executeRaw`
    DELETE FROM "PriceHistory" WHERE id IN (
      SELECT id FROM (
        SELECT id,
          row_number() OVER (PARTITION BY "skinId", date_trunc('day', "timestamp" AT TIME ZONE 'UTC')
                             ORDER BY "price" ASC,  "timestamp" ASC) AS rn_min,
          row_number() OVER (PARTITION BY "skinId", date_trunc('day', "timestamp" AT TIME ZONE 'UTC')
                             ORDER BY "price" DESC, "timestamp" ASC) AS rn_max
        FROM "PriceHistory"
        WHERE "timestamp" < ${rawCutoff} AND "timestamp" >= ${hardCutoff}
      ) t
      WHERE t.rn_min > 1 AND t.rn_max > 1
    )
  `

  // Solo armamos el guard tras completar ambos deletes con éxito — si cualquiera
  // lanza (blip de conexión, timeout, deadlock), no queremos silenciar el job
  // durante 24h sin haber liberado espacio realmente.
  lastRunAt = now

  log.info(
    `[PriceHistoryCleanup] Hard-deleted ${hardDeleted} rows (>${DAILY_RETENTION_DAYS}d), ` +
    `downsampled away ${downsampled} rows (${RAW_RETENTION_DAYS}–${DAILY_RETENTION_DAYS}d, kept daily min/max)`,
  )
}
