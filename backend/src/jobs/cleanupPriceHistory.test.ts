import { describe, it, expect, vi, beforeEach } from 'vitest'

// This job is the cause of the problem it now watches for. It deletes three of
// every four history points once they age past the raw band — half a million
// deletes a day — which leaves half-empty pages that VACUUM marks reusable but
// never compacts. Measured 2026-07-24: the table sat at 332 MB of a 500 MB
// ceiling and VACUUM FULL returned it to 136 MB.
//
// The warning is the whole point of that arrangement: compaction is manual, and
// the failure mode when the disk runs out is not "do maintenance", it is writes
// failing. A warning that never fires would be worse than none, so it is pinned
// from both sides.

const { executeRaw, queryRaw } = vi.hoisted(() => ({
  executeRaw: vi.fn(),
  queryRaw: vi.fn(),
}))

vi.mock('../db/prisma', () => ({
  prisma: { $executeRaw: executeRaw, $queryRaw: queryRaw },
}))

function makeLog() {
  return { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any
}

/** The module keeps `lastRunAt` in memory, so each test needs a fresh copy. */
async function freshJob() {
  vi.resetModules()
  return (await import('./cleanupPriceHistory')).cleanupPriceHistory
}

/** One row shaped like the storage query's result. */
function storage({ bytes, rows, dbBytes }: { bytes: number; rows: number; dbBytes: number }) {
  return [{ bytes: BigInt(bytes), rows: BigInt(rows), dbBytes: BigInt(dbBytes) }]
}

const MB = 1024 * 1024

/** Did any call to this mock mention the phrase? Handles pino's merge-object form. */
function mentioned(mock: ReturnType<typeof vi.fn>, phrase: string): boolean {
  return mock.mock.calls.some((call) =>
    call.some((arg: unknown) => typeof arg === 'string' && arg.includes(phrase)),
  )
}

describe('cleanupPriceHistory — storage watch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    executeRaw.mockResolvedValue(0)
  })

  it('warns that compaction is due when the table exceeds its packed size', async () => {
    // 500k rows in 200 MB is 400 B/row against a packed 231 — about 1.7x.
    queryRaw.mockResolvedValue(storage({ bytes: 200 * MB, rows: 500_000, dbBytes: 250 * MB }))
    const log = makeLog()

    await (await freshJob())(log)

    expect(mentioned(log.warn, 'packed size')).toBe(true)
    expect(mentioned(log.warn, 'compact-price-history')).toBe(true)
    // Bloat is recoverable maintenance; only the ceiling itself is an error.
    expect(log.error).not.toHaveBeenCalled()
  })

  it('stays quiet on a freshly compacted table', async () => {
    // 231 B/row exactly — the measured packed cost.
    queryRaw.mockResolvedValue(storage({ bytes: 110 * MB, rows: 500_000, dbBytes: 150 * MB }))
    const log = makeLog()

    await (await freshJob())(log)

    expect(log.warn).not.toHaveBeenCalled()
    expect(log.error).not.toHaveBeenCalled()
    // It should still report the size, so the trend is visible before it trips.
    expect(mentioned(log.info, 'B/row')).toBe(true)
  })

  it('escalates to error as the database approaches the free-tier ceiling', async () => {
    // Packed, but 450 MB of the 500 MB limit: compaction alone will not save it.
    queryRaw.mockResolvedValue(storage({ bytes: 110 * MB, rows: 500_000, dbBytes: 450 * MB }))
    const log = makeLog()

    await (await freshJob())(log)

    expect(mentioned(log.error, 'free-tier ceiling')).toBe(true)
  })

  it('does not let a failed size check break the cleanup', async () => {
    // The deletes have already happened by the time this runs. Losing the
    // measurement must not lose them.
    queryRaw.mockRejectedValue(new Error('connection reset'))
    const log = makeLog()

    await expect((await freshJob())(log)).resolves.toBeUndefined()

    expect(executeRaw).toHaveBeenCalledTimes(2)
    expect(mentioned(log.warn, 'Could not read storage size')).toBe(true)
  })

  it('skips entirely when it already ran within the day', async () => {
    queryRaw.mockResolvedValue(storage({ bytes: 110 * MB, rows: 500_000, dbBytes: 150 * MB }))
    const job = await freshJob()
    const log = makeLog()

    await job(log)
    executeRaw.mockClear()
    await job(log)

    expect(executeRaw).not.toHaveBeenCalled()
  })
})
