import { describe, it, expect } from 'vitest'
import Fastify from 'fastify'
import rateLimit from '@fastify/rate-limit'
import { parseTrustProxy } from './trustProxy'

describe('parseTrustProxy', () => {
  it('defaults to false when unset or empty', () => {
    expect(parseTrustProxy(undefined)).toBe(false)
    expect(parseTrustProxy('')).toBe(false)
    expect(parseTrustProxy('   ')).toBe(false)
  })

  it('maps the boolean strings to real booleans', () => {
    expect(parseTrustProxy('false')).toBe(false)
    expect(parseTrustProxy('true')).toBe(true)
  })

  it('maps a digit string to a numeric hop count, not a string', () => {
    // proxy-addr treats '1' (a string) as an IP allowlist and 1 as a hop count,
    // so the type coercion here is load-bearing.
    expect(parseTrustProxy('1')).toBe(1)
    expect(parseTrustProxy('2')).toBe(2)
    expect(parseTrustProxy(' 2 ')).toBe(2)
  })

  it('passes an IP/CIDR allowlist through untouched', () => {
    expect(parseTrustProxy('10.0.0.0/8,127.0.0.1')).toBe('10.0.0.0/8,127.0.0.1')
  })
})

/**
 * These lock in the behaviour the `trustProxy` setting actually buys us.
 *
 * Scenario mirrors production: the socket address is the proxy, the rightmost
 * X-Forwarded-For entry is the real client IP appended by that proxy, and the
 * leftmost entry is a value the client made up to try to escape rate limiting.
 */
const PROXY_ADDR = '10.0.0.7'
const REAL_CLIENT = '203.0.113.9'
const SPOOFED = '1.2.3.4'

async function resolveIp(trustProxy: boolean | number | string) {
  const app = Fastify({ trustProxy })
  app.get('/', async (request) => ({ ip: request.ip }))

  const res = await app.inject({
    method: 'GET',
    url: '/',
    remoteAddress: PROXY_ADDR,
    headers: { 'x-forwarded-for': `${SPOOFED}, ${REAL_CLIENT}` },
  })

  return res.json().ip
}

describe('request.ip resolution behind a proxy', () => {
  it('collapses every user into the proxy IP when trustProxy is off (the bug)', async () => {
    // This is today's production behaviour: every request shares one rate-limit
    // bucket keyed on the proxy, so a handful of users can 429 everyone.
    expect(await resolveIp(false)).toBe(PROXY_ADDR)
  })

  it('resolves the client-supplied leftmost entry when trustProxy is true (spoofable)', async () => {
    // Why we do NOT ship `trustProxy: true`: the resolved IP is attacker
    // controlled, so rotating the header mints unlimited rate-limit buckets.
    expect(await resolveIp(true)).toBe(SPOOFED)
  })

  it('resolves the real client IP with a hop count, ignoring the spoofed entry', async () => {
    expect(await resolveIp(1)).toBe(REAL_CLIENT)
  })

  it('keeps per-client buckets distinct with a hop count', async () => {
    const app = Fastify({ trustProxy: 1 })
    app.get('/', async (request) => ({ ip: request.ip }))

    const first = await app.inject({
      method: 'GET',
      url: '/',
      remoteAddress: PROXY_ADDR,
      headers: { 'x-forwarded-for': `${SPOOFED}, 198.51.100.4` },
    })
    const second = await app.inject({
      method: 'GET',
      url: '/',
      remoteAddress: PROXY_ADDR,
      headers: { 'x-forwarded-for': `${SPOOFED}, 198.51.100.5` },
    })

    expect(first.json().ip).toBe('198.51.100.4')
    expect(second.json().ip).toBe('198.51.100.5')
  })
})

/**
 * Replays the exact shape observed from production via GET /debug/client-ip on
 * 2026-07-16, so the configured TRUST_PROXY is pinned to real measurements
 * rather than an assumption about Render's internals. Render terminates TLS at a
 * container-local proxy (socket is 127.0.0.1) and the chain is
 * [client, Cloudflare edge, Render LB] — three trusted hops.
 */
describe('the measured Render topology', () => {
  const RENDER_SOCKET = '127.0.0.1'
  const CLIENT = '188.26.223.54'
  const MEASURED_HOPS = 3

  async function resolveVia(xff: string, trustProxy: number | boolean) {
    const app = Fastify({ trustProxy })
    app.get('/', async (request) => ({ ip: request.ip }))
    const res = await app.inject({
      method: 'GET',
      url: '/',
      remoteAddress: RENDER_SOCKET,
      headers: { 'x-forwarded-for': xff },
    })
    return res.json().ip
  }

  it('resolves the real client at the measured hop count', async () => {
    // Chain exactly as observed, Cloudflare edge + Render LB appended.
    const observed = `${CLIENT}, 104.22.23.18, 10.26.179.131`
    expect(await resolveVia(observed, MEASURED_HOPS)).toBe(CLIENT)
  })

  it('still resolves the real client when the caller forges a leading entry', async () => {
    // Observed when curling with `X-Forwarded-For: 9.9.9.9`: Render appends
    // rather than strips, so the forged value survives into the chain — it just
    // lands beyond the trusted hops and is ignored.
    const forged = `9.9.9.9, ${CLIENT}, 188.114.111.60, 10.31.118.132`
    expect(await resolveVia(forged, MEASURED_HOPS)).toBe(CLIENT)
  })

  it('would hand the forger their chosen IP under trustProxy: true', async () => {
    const forged = `9.9.9.9, ${CLIENT}, 188.114.111.60, 10.31.118.132`
    expect(await resolveVia(forged, true)).toBe('9.9.9.9')
  })

  it('collapses every caller onto localhost while trustProxy is off', async () => {
    // Matches production today: resolvedIp came back as 127.0.0.1 for everyone.
    const observed = `${CLIENT}, 104.22.23.18, 10.26.179.131`
    expect(await resolveVia(observed, false)).toBe(RENDER_SOCKET)
  })
})

/**
 * End-to-end through @fastify/rate-limit itself — request.ip resolving correctly
 * only matters if the limiter actually keys buckets off it.
 */
describe('@fastify/rate-limit bucketing behind a proxy', () => {
  async function buildLimitedApp(trustProxy: boolean | number) {
    const app = Fastify({ trustProxy })
    await app.register(rateLimit, { global: true, max: 2, timeWindow: '1 minute' })
    app.get('/', async () => ({ ok: true }))
    return app
  }

  const get = (app: Awaited<ReturnType<typeof buildLimitedApp>>, xff: string) =>
    app.inject({
      method: 'GET',
      url: '/',
      remoteAddress: PROXY_ADDR,
      headers: { 'x-forwarded-for': xff },
    })

  it('429s an innocent user because of someone else traffic when trustProxy is off', async () => {
    const app = await buildLimitedApp(false)

    // Two requests from one user exhaust the shared proxy-keyed bucket...
    await get(app, `${REAL_CLIENT}`)
    await get(app, `${REAL_CLIENT}`)
    // ...and an unrelated user is locked out. This is the reported bug.
    const victim = await get(app, '198.51.100.77')

    expect(victim.statusCode).toBe(429)
  })

  it('gives each real client its own bucket with a hop count', async () => {
    const app = await buildLimitedApp(1)

    await get(app, `${SPOOFED}, ${REAL_CLIENT}`)
    await get(app, `${SPOOFED}, ${REAL_CLIENT}`)
    const victim = await get(app, `${SPOOFED}, 198.51.100.77`)

    expect(victim.statusCode).toBe(200)
  })

  it('still caps a single client that forges X-Forwarded-For', async () => {
    const app = await buildLimitedApp(1)

    // Same real client each time, rotating the forged leftmost entry to try to
    // mint a fresh bucket per request. The hop count ignores it, so the cap holds.
    await get(app, `1.1.1.1, ${REAL_CLIENT}`)
    await get(app, `2.2.2.2, ${REAL_CLIENT}`)
    const third = await get(app, `3.3.3.3, ${REAL_CLIENT}`)

    expect(third.statusCode).toBe(429)
  })

  it('lets a forger escape the cap entirely when trustProxy is true', async () => {
    const app = await buildLimitedApp(true)

    await get(app, `1.1.1.1, ${REAL_CLIENT}`)
    await get(app, `2.2.2.2, ${REAL_CLIENT}`)
    const third = await get(app, `3.3.3.3, ${REAL_CLIENT}`)

    // Unlimited buckets — why `true` is not the fix.
    expect(third.statusCode).toBe(200)
  })
})
