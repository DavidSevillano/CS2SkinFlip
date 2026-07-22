import { prisma } from '../db/prisma'
import type { FastifyBaseLogger } from 'fastify'

// Resolución decreciente:
//   0–7 días   → raw (~4 pts/día, cada 6h)
//   7–90 días  → 1 pt/día (precio mínimo del día)
//   >90 días   → borrado
//
// La franja raw era de 14 días y se bajó a 7 (2026-07-22) por presupuesto de
// almacenamiento: el plan Free de Neon da 0.5 GB y la retención anterior proyectaba
// ~494 MB en régimen estacionario — dentro del límite, pero con margen cero.
//
// 7 días cuesta casi nada porque nada renderiza la resolución raw más allá del
// primer día: en `GET /skins/:id/price-history` sólo el rango `24h` usa
// `aggregateDaily: false`; 7d/30d/90d agregan por día. Lo único que se pierde es
// que los días 7–14 pasan de aportar mínimo y máximo diarios a un solo punto en
// los gráficos de 30d/90d.
//
// El suelo es CHANGE_REFERENCE_WINDOW_MS: la referencia de cambio 24h mira a la
// franja 24–48h, que tiene que seguir existiendo. A 7 días sobra de largo, pero
// no bajes de 2 sin mirar `config/priceHistory.ts`.
const RAW_RETENTION_DAYS = 7
const DAILY_RETENTION_DAYS = 90

// El job se invoca cada 6h; el downsample escanea toda la franja 14–90d, así que
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

  // 2) Downsample de la franja 14–90d: por (skinId, día UTC) conservar solo la
  //    fila con precio mínimo; borrar las demás. Idempotente.
  const downsampled = await prisma.$executeRaw`
    DELETE FROM "PriceHistory" WHERE id IN (
      SELECT id FROM (
        SELECT id,
          row_number() OVER (PARTITION BY "skinId", date_trunc('day', "timestamp" AT TIME ZONE 'UTC')
                             ORDER BY "price" ASC, "timestamp" ASC) AS rn_min
        FROM "PriceHistory"
        WHERE "timestamp" < ${rawCutoff} AND "timestamp" >= ${hardCutoff}
      ) t
      WHERE t.rn_min > 1
    )
  `

  // Solo armamos el guard tras completar ambos deletes con éxito — si cualquiera
  // lanza (blip de conexión, timeout, deadlock), no queremos silenciar el job
  // durante 24h sin haber liberado espacio realmente.
  lastRunAt = now

  log.info(
    `[PriceHistoryCleanup] Hard-deleted ${hardDeleted} rows (>${DAILY_RETENTION_DAYS}d), ` +
    `downsampled away ${downsampled} rows (${RAW_RETENTION_DAYS}–${DAILY_RETENTION_DAYS}d, kept daily min)`,
  )
}
