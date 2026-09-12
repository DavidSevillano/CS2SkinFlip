import { PrismaClient } from '@prisma/client'
import type { FastifyBaseLogger } from 'fastify'

// Cuánto headroom exigir sobre el tamaño del índice antes de reconstruirlo. El
// índice nuevo convive con el viejo hasta que termina, así que hace falta su
// tamaño entero; el 20% extra cubre que la tabla siga recibiendo escrituras
// mientras tanto.
const HEADROOM_MARGIN = 1.2

interface IndexRow {
  name: string
  bytes: bigint
  /** Bytes libres bajo neon.max_cluster_size, o null fuera de Neon. */
  headroom: bigint | null
}

/** Postgres quoting for an identifier that came back from the catalog. */
function quote(identifier: string): string {
  return `"${identifier.replace(/"/g, '""')}"`
}

/**
 * Reindexa `PriceHistory` en caliente.
 *
 * El hinchado de esta tabla es casi todo de índices, no de heap: al compactarla
 * el 2026-09-12 los índices pasaron de 280 a 146 MB (1,92x) mientras el heap
 * sólo bajó de 159 a 139 (1,14x) — 134 de los 154 MB recuperados. La causa es el
 * downsample, que borra 3 de cada 4 filas por (skinId, día): una página de hoja
 * de B-tree sólo se recicla cuando queda completamente vacía, y conservar 1 de
 * cada 4 entradas las deja al 25% para siempre.
 *
 * `REINDEX ... CONCURRENTLY` en vez de `VACUUM FULL` porque no toma ACCESS
 * EXCLUSIVE mientras trabaja. Es lo que hace aceptable lanzarlo sin supervisión
 * desde un job; `VACUUM FULL` bloquea lecturas y escrituras lo que dure.
 *
 * **Índice a índice, no `REINDEX TABLE`.** El 2026-09-12 una reescritura de esta
 * tabla murió con `53100: project size limit (512 MB) has been exceeded`:
 * `neon.max_cluster_size` es un tope duro y el índice nuevo convive con el viejo.
 * Los tres a la vez piden ~146 MB; de uno en uno el pico es el del mayor (79 MB).
 * Por eso también se comprueba el headroom antes de cada uno.
 *
 * Abre su propia conexión: REINDEX CONCURRENTLY no puede ir por un pooler en
 * modo transacción ni dentro de un bloque de transacción, así que el singleton
 * de `db/prisma` (que apunta a DATABASE_URL, el endpoint con pooler) no sirve.
 *
 * Devuelve `true` sólo si reindexó todo. Nunca lanza: quien lo llama ya ha hecho
 * el trabajo que importa.
 */
export async function reindexPriceHistory(log: FastifyBaseLogger): Promise<boolean> {
  const url = process.env.DIRECT_DATABASE_URL
  if (!url) {
    log.warn(
      '[PriceHistoryReindex] DIRECT_DATABASE_URL is not set — cannot reindex (REINDEX ' +
        'CONCURRENTLY does not work through a transaction-mode pooler). Set it in the Render ' +
        'dashboard, or run scripts/compact-price-history.ts by hand.',
    )
    return false
  }

  const client = new PrismaClient({ datasources: { db: { url } } })
  try {
    // Un REINDEX CONCURRENTLY fallido deja un índice inválido `*_ccnew` que
    // ocupa espacio y no se reintenta solo. Barrerlo antes es lo que evita que
    // se acumulen entre intentos.
    const invalid = await client.$queryRawUnsafe<Array<{ name: string }>>(
      `SELECT c.relname AS name
         FROM pg_index i
         JOIN pg_class c ON c.oid = i.indexrelid
         JOIN pg_class t ON t.oid = i.indrelid
        WHERE t.relname = 'PriceHistory' AND NOT i.indisvalid`,
    )
    for (const { name } of invalid) {
      log.warn(`[PriceHistoryReindex] Dropping leftover invalid index ${name}`)
      await client.$executeRawUnsafe(`DROP INDEX IF EXISTS ${quote(name)}`)
    }

    // `current_setting(..., true)` devuelve NULL donde el GUC no existe, y
    // pg_size_bytes(NULL) propaga el NULL: fuera de Neon no hay tope que mirar.
    const indexes = await client.$queryRawUnsafe<IndexRow[]>(
      `SELECT c.relname                AS name,
              pg_relation_size(c.oid)  AS bytes,
              pg_size_bytes(current_setting('neon.max_cluster_size', true))
                - pg_database_size(current_database()) AS headroom
         FROM pg_index i
         JOIN pg_class c ON c.oid = i.indexrelid
         JOIN pg_class t ON t.oid = i.indrelid
        WHERE t.relname = 'PriceHistory' AND i.indisvalid
        ORDER BY pg_relation_size(c.oid) DESC`,
    )

    const started = Date.now()
    for (const index of indexes) {
      const bytes = Number(index.bytes)
      // El headroom se mide una vez y se compara con todos: como vamos de mayor
      // a menor y cada reconstrucción libera espacio, sólo puede mejorar.
      if (index.headroom !== null) {
        const headroom = Number(index.headroom)
        if (headroom < bytes * HEADROOM_MARGIN) {
          log.error(
            `[PriceHistoryReindex] Not enough headroom to rebuild ${index.name}: it is ` +
              `${(bytes / 1024 / 1024).toFixed(1)} MB and only ` +
              `${(headroom / 1024 / 1024).toFixed(1)} MB is left under the cluster size limit. ` +
              `Free space first — a rebuild that hits the limit just wastes the run.`,
          )
          return false
        }
      }
      await client.$executeRawUnsafe(`REINDEX INDEX CONCURRENTLY ${quote(index.name)}`)
    }

    log.info(
      `[PriceHistoryReindex] Reindexed ${indexes.length} indexes in ` +
        `${((Date.now() - started) / 1000).toFixed(1)}s`,
    )
    return true
  } catch (err) {
    log.error({ err }, '[PriceHistoryReindex] Reindex failed')
    return false
  } finally {
    await client.$disconnect()
  }
}
