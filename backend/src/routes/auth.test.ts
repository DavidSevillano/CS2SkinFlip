import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'
import fastifyJwt from '@fastify/jwt'
import fastifyCookie from '@fastify/cookie'

const { userDelete, userFindUnique, userUpsert } = vi.hoisted(() => ({
  userDelete: vi.fn(),
  userFindUnique: vi.fn(),
  userUpsert: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: { user: { delete: userDelete, findUnique: userFindUnique, upsert: userUpsert } },
}))

const { axiosPost, axiosGet } = vi.hoisted(() => ({ axiosPost: vi.fn(), axiosGet: vi.fn() }))
vi.mock('axios', () => ({ default: { post: axiosPost, get: axiosGet } }))

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

const STEAM_ID = '76561198000000001'

/** A positive assertion as Steam would send it back to `returnTo`. */
function assertion(overrides: Record<string, string> = {}): string {
  return new URLSearchParams({
    'openid.ns': 'http://specs.openid.net/auth/2.0',
    'openid.mode': 'id_res',
    'openid.op_endpoint': 'https://steamcommunity.com/openid/login',
    'openid.claimed_id': `https://steamcommunity.com/openid/id/${STEAM_ID}`,
    'openid.identity': `https://steamcommunity.com/openid/id/${STEAM_ID}`,
    'openid.return_to': 'http://localhost:3000/auth/steam/callback',
    'openid.response_nonce': '2026-10-11T10:00:00Zabc',
    'openid.assoc_handle': '1234567890',
    'openid.signed': 'signed,op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle',
    'openid.sig': 'c2lnbmF0dXJl',
    ...overrides,
  }).toString()
}

describe('GET /auth/steam/callback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Steam vouches for every assertion in these tests: the signature is genuine.
    // What is under test is whether the assertion was minted for *us*.
    axiosPost.mockResolvedValue({ data: 'ns:http://specs.openid.net/auth/2.0\nis_valid:true\n' })
    axiosGet.mockResolvedValue({ data: { response: { players: [] } } })
    userUpsert.mockResolvedValue({ id: 'user-1', steamId: STEAM_ID })
  })

  it('signs in a genuine assertion addressed to our callback', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: `/auth/steam/callback?${assertion()}` })

    expect(res.statusCode).toBe(302)
    expect(res.headers.location).toMatch(/^cs2skinflip:\/\/auth\/callback\?token=/)
    expect(userUpsert).toHaveBeenCalled()
  })

  // A signed assertion obtained by another site that offers "Sign in through
  // Steam" — e.g. the victim logged in there — is still `is_valid:true` when
  // replayed to us, because Steam only checks its own signature and nonce.
  it('rejects a genuine assertion that was minted for another site', async () => {
    const app = await buildTestApp()

    const res = await app.inject({
      method: 'GET',
      url: `/auth/steam/callback?${assertion({ 'openid.return_to': 'https://evil.example/login/return' })}`,
    })

    expect(res.statusCode).toBe(401)
    expect(userUpsert).not.toHaveBeenCalled()
  })

  it('rejects a return_to that only shares our callback as a prefix', async () => {
    const app = await buildTestApp()

    const res = await app.inject({
      method: 'GET',
      url: `/auth/steam/callback?${assertion({ 'openid.return_to': 'http://localhost:3000/auth/steam/callback.evil' })}`,
    })

    expect(res.statusCode).toBe(401)
    expect(userUpsert).not.toHaveBeenCalled()
  })

  it('rejects an assertion from an OpenID provider other than Steam', async () => {
    const app = await buildTestApp()

    const res = await app.inject({
      method: 'GET',
      url: `/auth/steam/callback?${assertion({ 'openid.op_endpoint': 'https://evil.example/openid/login' })}`,
    })

    expect(res.statusCode).toBe(401)
    expect(userUpsert).not.toHaveBeenCalled()
  })

  it('rejects an assertion Steam says is invalid', async () => {
    axiosPost.mockResolvedValue({ data: 'ns:http://specs.openid.net/auth/2.0\nis_valid:false\n' })
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: `/auth/steam/callback?${assertion()}` })

    expect(res.statusCode).toBe(401)
    expect(userUpsert).not.toHaveBeenCalled()
  })
})

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
