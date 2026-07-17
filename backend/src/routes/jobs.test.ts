import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'

// vi.hoisted rather than a bare const: vitest hoists vi.mock calls above the rest
// of the file, so a factory closing over a const declared below it blows up with
// "Cannot access before initialization".
const { SECRET } = vi.hoisted(() => ({ SECRET: 'x'.repeat(32) }))

// `jobs.ts` imports `env`, whose module body calls process.exit(1) without the
// real env vars (absent under Vitest). Stub the one field it reads.
vi.mock('../config/env', () => ({ env: { JOBS_SECRET: SECRET } }))

const { runRefreshPrices, getRefreshStatus } = vi.hoisted(() => ({
  runRefreshPrices: vi.fn(),
  getRefreshStatus: vi.fn(),
}))
vi.mock('../jobs/runner', () => ({ runRefreshPrices, getRefreshStatus }))

const { jobsRoutes } = await import('./jobs')

async function buildTestApp() {
  const app = Fastify()
  await app.register(jobsRoutes)
  return app
}

describe('POST /jobs/refresh-prices', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    runRefreshPrices.mockResolvedValue({ started: true, runId: 'run-1' })
  })

  it('rejects a request with no secret', async () => {
    const app = await buildTestApp()
    const res = await app.inject({ method: 'POST', url: '/jobs/refresh-prices' })

    expect(res.statusCode).toBe(401)
    expect(runRefreshPrices).not.toHaveBeenCalled()
  })

  it('rejects a request with the wrong secret', async () => {
    const app = await buildTestApp()
    const res = await app.inject({
      method: 'POST',
      url: '/jobs/refresh-prices',
      headers: { 'x-jobs-secret': 'nope' },
    })

    expect(res.statusCode).toBe(401)
    expect(runRefreshPrices).not.toHaveBeenCalled()
  })

  it('returns 202 with the runId when the run starts', async () => {
    const app = await buildTestApp()
    const res = await app.inject({
      method: 'POST',
      url: '/jobs/refresh-prices',
      headers: { 'x-jobs-secret': SECRET },
    })

    expect(res.statusCode).toBe(202)
    expect(res.json()).toEqual({ ok: true, runId: 'run-1' })
  })

  it('returns 409 when a run is already in flight', async () => {
    runRefreshPrices.mockResolvedValue({ started: false, reason: 'already-running' })
    const app = await buildTestApp()
    const res = await app.inject({
      method: 'POST',
      url: '/jobs/refresh-prices',
      headers: { 'x-jobs-secret': SECRET },
    })

    expect(res.statusCode).toBe(409)
    expect(res.json()).toEqual({ error: 'already-running' })
  })
})

describe('GET /jobs/refresh-prices/status', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns 404 when no run has been recorded', async () => {
    getRefreshStatus.mockResolvedValue(null)
    const app = await buildTestApp()
    const res = await app.inject({
      method: 'GET',
      url: '/jobs/refresh-prices/status',
      headers: { 'x-jobs-secret': SECRET },
    })

    expect(res.statusCode).toBe(404)
  })

  it('returns the status of the last run', async () => {
    getRefreshStatus.mockResolvedValue({ runId: 'run-1', state: 'ok', startedAt: 'now', updated: 42 })
    const app = await buildTestApp()
    const res = await app.inject({
      method: 'GET',
      url: '/jobs/refresh-prices/status',
      headers: { 'x-jobs-secret': SECRET },
    })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({ runId: 'run-1', state: 'ok', updated: 42 })
  })

  it('rejects a status request with no secret', async () => {
    const app = await buildTestApp()
    const res = await app.inject({ method: 'GET', url: '/jobs/refresh-prices/status' })

    expect(res.statusCode).toBe(401)
    expect(getRefreshStatus).not.toHaveBeenCalled()
  })
})

describe('jobsRoutes without JOBS_SECRET', () => {
  it('does not register the routes at all', async () => {
    vi.resetModules()
    vi.doMock('../config/env', () => ({ env: { JOBS_SECRET: undefined } }))
    vi.doMock('../jobs/runner', () => ({ runRefreshPrices, getRefreshStatus }))

    const { jobsRoutes: disabledRoutes } = await import('./jobs')
    const app = Fastify()
    await app.register(disabledRoutes)
    const res = await app.inject({ method: 'POST', url: '/jobs/refresh-prices' })

    // 404, not 401: the route must not exist at all, rather than exist and reject.
    expect(res.statusCode).toBe(404)

    vi.doUnmock('../config/env')
    vi.doUnmock('../jobs/runner')
  })
})
