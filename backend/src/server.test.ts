import { describe, it, expect, vi, beforeEach } from 'vitest'

// UptimeRobot watches /health as a keyword monitor looking for `fresh`, so the
// exact serialized payload is the contract — not just getPriceHealth's return
// value. These drive the real route to assert on the bytes the monitor parses.

// `env`'s module body calls process.exit(1) when the real vars are absent (they
// are, under Vitest). Stub the fields buildServer reads.
vi.mock('./config/env', () => ({
  env: {
    NODE_ENV: 'test',
    LOG_LEVEL: 'silent',
    TRUST_PROXY: 1,
    FRONTEND_URL: 'http://localhost:5173',
    JWT_SECRET: 'test-secret-at-least-32-characters-long',
    RATE_LIMIT_MAX: 100,
    RATE_LIMIT_WINDOW: '1 minute',
  },
}))

// registerRoutes pulls in every route module (prisma, redis, fcm, googlePlay).
// /health is defined in server.ts itself, so the rest of the tree is noise here.
vi.mock('./routes', () => ({ registerRoutes: vi.fn() }))
vi.mock('./db/prisma', () => ({ prisma: {} }))

const { redisGet } = vi.hoisted(() => ({ redisGet: vi.fn() }))
vi.mock('./redis/client', () => ({ redis: { get: redisGet, set: vi.fn(), del: vi.fn() } }))

const { buildServer } = await import('./server')

async function getHealth() {
  const app = await buildServer()
  const res = await app.inject({ method: 'GET', url: '/health' })
  await app.close()
  return res
}

describe('GET /health', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reports prices fresh after a recent successful run', async () => {
    redisGet.mockResolvedValue(Date.now() - 60 * 60 * 1000)

    const res = await getHealth()

    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({ status: 'ok', prices: 'fresh' })
    expect(res.body).toContain('fresh')
  })

  it('reports prices stale when the last successful run is over 8h old', async () => {
    redisGet.mockResolvedValue(Date.now() - 9 * 60 * 60 * 1000)

    const res = await getHealth()

    // Still 200: the process is up. The keyword is what tells the monitor the
    // pipeline is dead, so `fresh` must not appear anywhere in the payload.
    expect(res.statusCode).toBe(200)
    expect(res.json().prices).toBe('stale')
    expect(res.body).not.toContain('fresh')
  })

  it('does not fail the request when Redis is unreachable', async () => {
    redisGet.mockRejectedValue(new Error('upstash unreachable'))

    const res = await getHealth()

    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({ prices: 'unknown', pricesLastRun: null })
    expect(res.body).not.toContain('fresh')
  })

  it('exposes the last run timestamp for debugging a fired alert', async () => {
    const lastRun = Date.now() - 60 * 60 * 1000
    redisGet.mockResolvedValue(lastRun)

    const res = await getHealth()

    expect(res.json().pricesLastRun).toBe(new Date(lastRun).toISOString())
  })
})
