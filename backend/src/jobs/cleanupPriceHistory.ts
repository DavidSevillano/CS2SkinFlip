import { prisma } from '../db/prisma'
import type { FastifyBaseLogger } from 'fastify'
import { reindexPriceHistory } from './reindexPriceHistory'

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

// ─── Vigilancia de espacio ───────────────────────────────────────────────────
// Este job es la causa del problema que vigila. Borra 3 de cada 4 puntos al
// pasar la franja raw — medio millón de borrados al día — y eso deja páginas a
// medias que VACUUM marca reutilizables pero nunca compacta, mientras las filas
// nuevas se añaden al final. Medido 2026-07-24: la tabla ocupaba 332 MB de un
// techo de 500 y `VACUUM FULL` la dejó en 136 MB sin perder una sola fila.
//
// El hinchado vuelve, así que hay que repetirlo — y ahora el propio job lo
// hace: al pasar el umbral de abajo dispara `reindexPriceHistory`, que
// reconstruye los índices online con `REINDEX INDEX CONCURRENTLY`. Antes esto
// solo avisaba y dependía de que alguien se acordara de correr el script de
// compactación a mano; ese aviso llevaba semanas sonando sin que nadie lo
// leyera, que es como la base llegó a su techo. `REINDEX` solo reconstruye
// índices, no el heap: `scripts/compact-price-history.ts` (VACUUM FULL) sigue
// siendo necesario a mano para ese lado, ya que bloquea la tabla y necesita
// `DIRECT_DATABASE_URL`.
//
// Coste real por fila con la tabla compactada, incluidos índices (medido, no
// estimado). Proyectar con este número una tabla hinchada subestima por 2,5x —
// es el error que ya llevó dos veces a la conclusión equivocada de que el
// problema era la retención.
const PACKED_BYTES_PER_ROW = 231

// A 1,35x el reindexado sale barato y sobra margen: por debajo de esto el
// hinchado no acerca la base al techo, y como REINDEX CONCURRENTLY no bloquea,
// no hay razón para dejar que se acumule más. Cuanto antes se recorte, menos
// espacio transitorio hace falta para recortarlo — y el tope de cluster de Neon
// (512 MB) es duro.
const REINDEX_BLOAT_RATIO = 1.35

const STORAGE_CEILING_BYTES = 500 * 1024 * 1024
const STORAGE_WARN_RATIO = 0.8

/**
 * Informa del tamaño de `PriceHistory` y reindexa cuando toca.
 *
 * No compacta el heap por su cuenta: eso sigue siendo `VACUUM FULL` a mano
 * (`scripts/compact-price-history.ts`), que bloquea la tabla y necesita
 * `DIRECT_DATABASE_URL`, que Render no define.
 */
async function reportStorage(log: FastifyBaseLogger) {
  try {
    const [row] = await prisma.$queryRaw<Array<{ bytes: bigint; rows: bigint; dbBytes: bigint }>>`
      SELECT pg_total_relation_size('"PriceHistory"')      AS bytes,
             (SELECT COUNT(*) FROM "PriceHistory")::bigint AS rows,
             pg_database_size(current_database())          AS "dbBytes"
    `
    const bytes = Number(row.bytes)
    const rows = Number(row.rows)
    const dbBytes = Number(row.dbBytes)
    if (rows === 0) return

    const bytesPerRow = bytes / rows
    const bloat = bytesPerRow / PACKED_BYTES_PER_ROW
    const mb = (n: number) => (n / 1024 / 1024).toFixed(1)

    log.info(
      `[PriceHistoryCleanup] PriceHistory ${mb(bytes)} MB / ${rows} rows ` +
        `(${bytesPerRow.toFixed(0)} B/row, ${bloat.toFixed(2)}x packed) — database ${mb(dbBytes)} MB`,
    )

    if (bloat >= REINDEX_BLOAT_RATIO) {
      log.info(
        `[PriceHistoryCleanup] PriceHistory is ${bloat.toFixed(2)}x its packed size — ` +
          `~${mb(bytes - rows * PACKED_BYTES_PER_ROW)} MB of reclaimable slack. Reindexing.`,
      )
      await reindexPriceHistory(log)
    }

    if (dbBytes >= STORAGE_CEILING_BYTES * STORAGE_WARN_RATIO) {
      log.error(
        `[PriceHistoryCleanup] Database is ${mb(dbBytes)} MB of a ${mb(STORAGE_CEILING_BYTES)} MB ` +
          `free-tier ceiling. Writes start failing at the limit — compact first, and if that is ` +
          `not enough the retention bands in this file are the next lever.`,
      )
    }
  } catch (err) {
    // Vigilar el espacio no puede ser el motivo de que la limpieza falle: para
    // cuando esto importe, los borrados de arriba ya se han hecho.
    log.warn({ err }, '[PriceHistoryCleanup] Could not read storage size')
  }
}

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

  // Después de los borrados, que es cuando el hinchado que deja este job es
  // máximo y la medida más representativa.
  await reportStorage(log)
}
