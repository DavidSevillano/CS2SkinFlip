import { describe, it, expect, vi, beforeEach } from 'vitest'

const { redisSet, redisGet, redisDel } = vi.hoisted(() => ({
  redisSet: vi.fn(),
  redisGet: vi.fn(),
  redisDel: vi.fn(),
}))
vi.mock('../redis/client', () => ({ redis: { set: redisSet, get: redisGet, del: redisDel } }))

const { populatePrices } = vi.hoisted(() => ({ populatePrices: vi.fn() }))
vi.mock('./populatePrices', () => ({ populatePrices }))

const { cleanupPriceHistory } = vi.hoisted(() => ({ cleanupPriceHistory: vi.fn() }))
vi.mock('./cleanupPriceHistory', () => ({ cleanupPriceHistory }))

// runner.ts constructs an AlertService in its module body; the class mock gives it
// a spyable checkAll without dragging in the real prisma/redis.
const { checkAll } = vi.hoisted(() => ({ checkAll: vi.fn() }))
vi.mock('../services/alerts', () => ({ AlertService: class { checkAll = checkAll } }))

const { runRefreshPrices, getRefreshStatus, REFRESH_LOCK_KEY, REFRESH_STATUS_KEY } =
  await import('./runner')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

/** Last JSON written to the status key. */
function lastStatus() {
  const calls = redisSet.mock.calls.filter((c) => c[0] === REFRESH_STATUS_KEY)
  if (calls.length === 0) throw new Error('no status written')
  return JSON.parse(calls[calls.length - 1][1])
}

describe('runRefreshPrices', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    redisSet.mockResolvedValue('OK')
    redisDel.mockResolvedValue(1)
    populatePrices.mockResolvedValue({ updated: 100, historyRows: 100 })
    checkAll.mockResolvedValue(undefined)
    cleanupPriceHistory.mockResolvedValue(undefined)
  })

  it('takes the lock with NX and a TTL so a crashed run self-heals', async () => {
    await runRefreshPrices(log)

    const lockCall = redisSet.mock.calls.find((c) => c[0] === REFRESH_LOCK_KEY)
    expect(lockCall).toBeDefined()
    expect(lockCall![2]).toMatchObject({ nx: true })
    expect(lockCall![2].ex).toBeGreaterThan(0)
  })

  it('refuses to start a second run while the lock is held', async () => {
    redisSet.mockImplementation(async (key: string) => (key === REFRESH_LOCK_KEY ? null : 'OK'))

    const result = await runRefreshPrices(log)

    expect(result).toEqual({ started: false, reason: 'already-running' })
    expect(populatePrices).not.toHaveBeenCalled()
  })

  it('marks the run failed when every marketplace returned nothing', async () => {
    populatePrices.mockResolvedValue({ updated: 0, historyRows: 0 })

    const result = await runRefreshPrices(log)
    expect(result).toMatchObject({ started: true })

    await vi.waitFor(() => expect(lastStatus().state).toBe('failed'))
    // No fresh price means nothing to check alerts against.
    expect(checkAll).not.toHaveBeenCalled()
    expect(cleanupPriceHistory).not.toHaveBeenCalled()
    expect(redisDel).toHaveBeenCalledWith(REFRESH_LOCK_KEY)
  })

  it('marks the run ok and releases the lock on the happy path', async () => {
    const result = await runRefreshPrices(log)
    expect(result).toMatchObject({ started: true })

    await vi.waitFor(() => expect(lastStatus().state).toBe('ok'))
    const status = lastStatus()
    expect(status.updated).toBe(100)
    expect(status.runId).toBe((result as { runId: string }).runId)
    expect(typeof status.durationMs).toBe('number')
    expect(checkAll).toHaveBeenCalledTimes(1)
    expect(cleanupPriceHistory).toHaveBeenCalledTimes(1)
    expect(redisDel).toHaveBeenCalledWith(REFRESH_LOCK_KEY)
  })

  it('captures a thrown error into the status and still releases the lock', async () => {
    populatePrices.mockRejectedValue(new Error('Neon exploded'))

    await runRefreshPrices(log)

    await vi.waitFor(() => expect(lastStatus().state).toBe('failed'))
    expect(lastStatus().error).toContain('Neon exploded')
    expect(redisDel).toHaveBeenCalledWith(REFRESH_LOCK_KEY)
  })

  it('writes the running status before returning, so the first poll cannot miss it', async () => {
    await runRefreshPrices(log)

    const firstStatusCall = redisSet.mock.calls.find((c) => c[0] === REFRESH_STATUS_KEY)
    expect(JSON.parse(firstStatusCall![1]).state).toBe('running')
  })
})

describe('getRefreshStatus', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reports no status before any run has been recorded', async () => {
    redisGet.mockResolvedValue(null)

    expect(await getRefreshStatus()).toBeNull()
  })

  it('returns the status Upstash already deserialised into an object', async () => {
    redisGet.mockResolvedValue({ runId: 'run-1', state: 'ok', startedAt: 'now', updated: 42 })

    expect(await getRefreshStatus()).toMatchObject({ runId: 'run-1', state: 'ok', updated: 42 })
  })

  it('parses a status that comes back as a raw JSON string', async () => {
    redisGet.mockResolvedValue(JSON.stringify({ runId: 'run-2', state: 'running', startedAt: 'now' }))

    expect(await getRefreshStatus()).toMatchObject({ runId: 'run-2', state: 'running' })
  })
})
