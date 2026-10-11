import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'
import fastifyJwt from '@fastify/jwt'

const { userFindUnique } = vi.hoisted(() => ({ userFindUnique: vi.fn() }))
vi.mock('../db/prisma', () => ({ prisma: { user: { findUnique: userFindUnique } } }))

const { authenticate } = await import('./authenticate')

const DAY = 24 * 60 * 60

async function buildTestApp() {
  const app = Fastify()
  await app.register(fastifyJwt, { secret: 'test-secret-at-least-32-characters-long' })
  app.get('/private', { onRequest: [authenticate] }, async () => ({ ok: true }))
  return app
}

function claims(token: string): Record<string, number | string> {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
}

describe('authenticate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userFindUnique.mockResolvedValue({ id: 'user-1' })
  })

  it('leaves a token with plenty of life left alone', async () => {
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1', steamId: 's' }, { expiresIn: '30d' })

    const res = await app.inject({ method: 'GET', url: '/private', headers: { authorization: `Bearer ${token}` } })

    expect(res.statusCode).toBe(200)
    expect(res.headers['x-refreshed-token']).toBeUndefined()
  })

  // Sliding renewal: an active user never hits the 30-day expiry.
  it('hands back a fresh 30-day token when the current one expires within a week', async () => {
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1', steamId: 's' }, { expiresIn: '3d' })

    const res = await app.inject({ method: 'GET', url: '/private', headers: { authorization: `Bearer ${token}` } })

    expect(res.statusCode).toBe(200)
    const fresh = claims(res.headers['x-refreshed-token'] as string)
    expect(fresh.userId).toBe('user-1')
    expect(fresh.steamId).toBe('s')
    expect((fresh.exp as number) - (fresh.iat as number)).toBe(30 * DAY)
  })

  // Tokens issued before expiry existed carry no `exp`. Still accepted while
  // legacy app versions are around, but upgraded to an expiring one on first use.
  it('upgrades a legacy token that has no expiry', async () => {
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1', steamId: 's' })

    const res = await app.inject({ method: 'GET', url: '/private', headers: { authorization: `Bearer ${token}` } })

    expect(res.statusCode).toBe(200)
    expect(claims(res.headers['x-refreshed-token'] as string).exp).toBeDefined()
  })

  it('rejects an expired token', async () => {
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1', steamId: 's', iat: Math.floor(Date.now() / 1000) - 40 * DAY }, { expiresIn: '30d' })

    const res = await app.inject({ method: 'GET', url: '/private', headers: { authorization: `Bearer ${token}` } })

    expect(res.statusCode).toBe(401)
    expect(res.headers['x-refreshed-token']).toBeUndefined()
  })

  it('does not refresh a token whose user no longer exists', async () => {
    userFindUnique.mockResolvedValue(null)
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'gone', steamId: 's' })

    const res = await app.inject({ method: 'GET', url: '/private', headers: { authorization: `Bearer ${token}` } })

    expect(res.statusCode).toBe(401)
    expect(res.headers['x-refreshed-token']).toBeUndefined()
  })
})
