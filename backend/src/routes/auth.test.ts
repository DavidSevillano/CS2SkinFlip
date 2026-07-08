import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'
import fastifyJwt from '@fastify/jwt'
import fastifyCookie from '@fastify/cookie'

const { userDelete, userFindUnique } = vi.hoisted(() => ({
  userDelete: vi.fn(),
  userFindUnique: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: { user: { delete: userDelete, findUnique: userFindUnique } },
}))

vi.mock('../config/env', () => ({
  env: {
    STEAM_API_KEY: 'test-key',
    STEAM_CALLBACK_URL: 'http://localhost:3000/auth/steam/callback',
    FRONTEND_URL: 'http://localhost:8080',
    MOBILE_DEEP_LINK: 'cs2skinflip://auth/callback',
    NODE_ENV: 'test',
  },
}))

const { authRoutes } = await import('./auth')

async function buildTestApp() {
  const app = Fastify()
  await app.register(fastifyJwt, { secret: 'test-secret-at-least-32-characters-long' })
  await app.register(fastifyCookie)
  await app.register(authRoutes)
  return app
}

describe('DELETE /auth/me', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userFindUnique.mockResolvedValue({ id: 'user-1' })
  })

  it('rejects requests without a valid JWT', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'DELETE', url: '/auth/me' })

    expect(res.statusCode).toBe(401)
    expect(userDelete).not.toHaveBeenCalled()
  })

  it('deletes the caller\'s user record and clears the auth cookie', async () => {
    userDelete.mockResolvedValue({ id: 'user-1' })
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1' })

    const res = await app.inject({
      method: 'DELETE',
      url: '/auth/me',
      headers: { authorization: `Bearer ${token}` },
    })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ success: true })
    expect(userDelete).toHaveBeenCalledWith({ where: { id: 'user-1' } })
    expect(res.headers['set-cookie']).toMatch(/token=;/)
  })
})
