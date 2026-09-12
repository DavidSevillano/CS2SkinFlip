import { describe, it, expect, vi, beforeEach } from 'vitest'

// REINDEX CONCURRENTLY no puede ir por el pooler ni dentro de una transacción,
// así que este módulo abre su propia conexión directa en vez de reutilizar el
// singleton de `db/prisma`. Sin DIRECT_DATABASE_URL no puede hacer nada, y ese
// caso tiene que degradar a un aviso: es el estado en el que vive Render hoy.
//
// Va índice a índice, no `REINDEX TABLE`, por el tope duro de Neon: el
// 2026-09-12 una reescritura de esta tabla murió con 53100 "project size limit
// (512 MB) has been exceeded". Reconstruir los tres índices a la vez pide ~146
// MB, y con la tabla hinchada no hay tanto headroom. De uno en uno el pico es
// el del índice mayor.

const { queryRawUnsafe, executeRawUnsafe, disconnect, ctor } = vi.hoisted(() => ({
  queryRawUnsafe: vi.fn(),
  executeRawUnsafe: vi.fn(),
  disconnect: vi.fn(),
  ctor: vi.fn(),
}))

vi.mock('@prisma/client', () => ({
  PrismaClient: class {
    constructor(opts: unknown) {
      ctor(opts)
    }
    $queryRawUnsafe = queryRawUnsafe
    $executeRawUnsafe = executeRawUnsafe
    $disconnect = disconnect
  },
}))

function makeLog() {
  return { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any
}

function mentioned(mock: ReturnType<typeof vi.fn>, phrase: string): boolean {
  return mock.mock.calls.some((call) =>
    call.some((arg: unknown) => typeof arg === 'string' && arg.includes(phrase)),
  )
}

/** Every statement handed to $executeRawUnsafe, in order. */
function statements(): string[] {
  return executeRawUnsafe.mock.calls.map((c) => String(c[0]))
}

const MB = 1024 * 1024

/**
 * The module issues two reads in order: invalid-index sweep, then the index
 * inventory. `headroom` is bytes left under neon.max_cluster_size, or null off
 * Neon, where the setting does not exist.
 */
function reads({
  invalid = [] as string[],
  indexes = [] as Array<{ name: string; bytes: number }>,
  headroom = null as number | null,
}) {
  queryRawUnsafe
    .mockResolvedValueOnce(invalid.map((name) => ({ name })))
    .mockResolvedValueOnce(
      indexes.map((i) => ({
        name: i.name,
        bytes: BigInt(i.bytes),
        headroom: headroom === null ? null : BigInt(headroom),
      })),
    )
}

describe('reindexPriceHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.resetModules()
    process.env.DIRECT_DATABASE_URL = 'postgres://direct/db'
    executeRawUnsafe.mockResolvedValue(0)
  })

  it('reindexes every index concurrently over the direct connection', async () => {
    reads({
      indexes: [
        { name: 'PriceHistory_skinId_timestamp_idx', bytes: 79 * MB },
        { name: 'PriceHistory_pkey', bytes: 59 * MB },
      ],
      headroom: 300 * MB,
    })
    const { reindexPriceHistory } = await import('./reindexPriceHistory')

    const done = await reindexPriceHistory(makeLog())

    expect(done).toBe(true)
    expect(statements()).toEqual([
      'REINDEX INDEX CONCURRENTLY "PriceHistory_skinId_timestamp_idx"',
      'REINDEX INDEX CONCURRENTLY "PriceHistory_pkey"',
    ])
    // One at a time is the whole point — REINDEX TABLE would rebuild all three
    // at once and blow the cluster size limit.
    expect(statements().join('\n')).not.toContain('REINDEX TABLE')
    expect(statements().join('\n')).not.toContain('VACUUM')
    expect(ctor).toHaveBeenCalledWith({ datasources: { db: { url: 'postgres://direct/db' } } })
    expect(disconnect).toHaveBeenCalled()
  })

  it('does nothing but warn when DIRECT_DATABASE_URL is absent', async () => {
    delete process.env.DIRECT_DATABASE_URL
    const { reindexPriceHistory } = await import('./reindexPriceHistory')
    const log = makeLog()

    const done = await reindexPriceHistory(log)

    expect(done).toBe(false)
    expect(executeRawUnsafe).not.toHaveBeenCalled()
    expect(mentioned(log.warn, 'DIRECT_DATABASE_URL')).toBe(true)
  })

  it('drops leftover invalid indexes before reindexing', async () => {
    // A failed REINDEX CONCURRENTLY leaves a *_ccnew index behind. It occupies
    // space and is never retried, so it has to be swept or it accumulates.
    reads({
      invalid: ['PriceHistory_pkey_ccnew'],
      indexes: [{ name: 'PriceHistory_pkey', bytes: 59 * MB }],
      headroom: 300 * MB,
    })
    const { reindexPriceHistory } = await import('./reindexPriceHistory')

    await reindexPriceHistory(makeLog())

    expect(statements()[0]).toBe('DROP INDEX IF EXISTS "PriceHistory_pkey_ccnew"')
    expect(statements()[1]).toContain('REINDEX INDEX CONCURRENTLY')
  })

  it('refuses to start an index that will not fit under the cluster size limit', async () => {
    // 79 MB index, 50 MB of headroom. Attempting it just burns a run and logs
    // a 53100; better to say so and leave the space alone.
    reads({
      indexes: [{ name: 'PriceHistory_skinId_timestamp_idx', bytes: 79 * MB }],
      headroom: 50 * MB,
    })
    const { reindexPriceHistory } = await import('./reindexPriceHistory')
    const log = makeLog()

    const done = await reindexPriceHistory(log)

    expect(done).toBe(false)
    expect(statements()).toEqual([])
    expect(mentioned(log.error, 'headroom')).toBe(true)
  })

  it('proceeds when the size limit is unknown, as off Neon', async () => {
    reads({ indexes: [{ name: 'PriceHistory_pkey', bytes: 59 * MB }], headroom: null })
    const { reindexPriceHistory } = await import('./reindexPriceHistory')

    const done = await reindexPriceHistory(makeLog())

    expect(done).toBe(true)
    expect(statements()).toEqual(['REINDEX INDEX CONCURRENTLY "PriceHistory_pkey"'])
  })

  it('reports failure instead of throwing, and still disconnects', async () => {
    reads({ indexes: [{ name: 'PriceHistory_pkey', bytes: 59 * MB }], headroom: 300 * MB })
    executeRawUnsafe.mockRejectedValue(new Error('deadlock detected'))
    const { reindexPriceHistory } = await import('./reindexPriceHistory')
    const log = makeLog()

    const done = await reindexPriceHistory(log)

    expect(done).toBe(false)
    expect(log.error).toHaveBeenCalled()
    expect(disconnect).toHaveBeenCalled()
  })
})
