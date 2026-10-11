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

const { redis } = await vi.hoisted(async () => {
  const { fakeRedisStore } = await import('../test/fakeRedis')
  return { redis: fakeRedisStore() }
})
vi.mock('../redis/client', () => ({ redis }))

// A fixed verifier and its S256 challenge, base64url(sha256(verifier)),
// precomputed independently. The Android side pins the same pair.
const VERIFIER = 'dBjftJeZ4CVP-mJ0K33TzhIDn7RnPd5Q0HOdODN1hEk'
const CHALLENGE = '0sD9xcv3ROe19ThujFtMtauXJzymPhgUDlDgWgv1AL8'

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

function returnToOf(location: string): URL {
  return new URL(new URL(location).searchParams.get('openid.return_to')!)
}

function decodeJwt(token: string): Record<string, unknown> {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
}

describe('GET /auth/steam', () => {
  it('carries the app\'s PKCE challenge through Steam on the return_to', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: `/auth/steam?challenge=${CHALLENGE}` })

    expect(res.statusCode).toBe(302)
    const returnTo = returnToOf(res.headers.location as string)
    expect(returnTo.origin + returnTo.pathname).toBe('http://localhost:3000/auth/steam/callback')
    expect(returnTo.searchParams.get('challenge')).toBe(CHALLENGE)
  })

  it('keeps the legacy return_to for app versions that send no challenge', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/auth/steam' })

    expect(returnToOf(res.headers.location as string).toString()).toBe('http://localhost:3000/auth/steam/callback')
  })

  it('rejects a malformed challenge', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/auth/steam?challenge=short' })

    expect(res.statusCode).toBe(400)
  })
})

describe('PKCE sign-in', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    redis.store.clear()
    axiosPost.mockResolvedValue({ data: 'ns:http://specs.openid.net/auth/2.0\nis_valid:true\n' })
    axiosGet.mockResolvedValue({ data: { response: { players: [] } } })
    userUpsert.mockResolvedValue({ id: 'user-1', steamId: STEAM_ID })
  })

  async function signInWithChallenge(app: Awaited<ReturnType<typeof buildTestApp>>) {
    const returnTo = `http://localhost:3000/auth/steam/callback?challenge=${CHALLENGE}`
    return app.inject({
      method: 'GET',
      url: `/auth/steam/callback?challenge=${CHALLENGE}&${assertion({ 'openid.return_to': returnTo })}`,
    })
  }

  // A JWT on the deep link can be read by any app that registers the same
  // scheme, and a link carrying an attacker's JWT signs the victim in to the
  // attacker's account. A code is useless without the verifier only our app holds.
  it('hands the app a one-time code, never the JWT itself', async () => {
    const app = await buildTestApp()

    const res = await signInWithChallenge(app)

    expect(res.statusCode).toBe(302)
    const location = new URL(res.headers.location as string)
    expect(location.protocol).toBe('cs2skinflip:')
    expect(location.searchParams.get('token')).toBeNull()
    expect(location.searchParams.get('code')).toMatch(/^[A-Za-z0-9_-]{40,}$/)
    expect(res.headers['set-cookie']).toBeUndefined()
  })

  it('exchanges the code for a 30-day JWT when the verifier matches', async () => {
    const app = await buildTestApp()
    const code = new URL((await signInWithChallenge(app)).headers.location as string).searchParams.get('code')

    const res = await app.inject({ method: 'POST', url: '/auth/exchange', payload: { code, verifier: VERIFIER } })

    expect(res.statusCode).toBe(200)
    const claims = decodeJwt(res.json().token)
    expect(claims.userId).toBe('user-1')
    expect(claims.steamId).toBe(STEAM_ID)
    expect((claims.exp as number) - (claims.iat as number)).toBe(30 * 24 * 60 * 60)
  })

  it('refuses the code with the wrong verifier, and burns it', async () => {
    const app = await buildTestApp()
    const code = new URL((await signInWithChallenge(app)).headers.location as string).searchParams.get('code')

    const wrong = await app.inject({
      method: 'POST',
      url: '/auth/exchange',
      payload: { code, verifier: 'x'.repeat(43) },
    })
    const retry = await app.inject({ method: 'POST', url: '/auth/exchange', payload: { code, verifier: VERIFIER } })

    expect(wrong.statusCode).toBe(400)
    expect(retry.statusCode).toBe(400)
  })

  it('refuses to redeem the same code twice', async () => {
    const app = await buildTestApp()
    const code = new URL((await signInWithChallenge(app)).headers.location as string).searchParams.get('code')

    await app.inject({ method: 'POST', url: '/auth/exchange', payload: { code, verifier: VERIFIER } })
    const second = await app.inject({ method: 'POST', url: '/auth/exchange', payload: { code, verifier: VERIFIER } })

    expect(second.statusCode).toBe(400)
  })

  it('refuses an unknown code', async () => {
    const app = await buildTestApp()

    const res = await app.inject({
      method: 'POST',
      url: '/auth/exchange',
      payload: { code: 'never-issued-code-never-issued-code-never', verifier: VERIFIER },
    })

    expect(res.statusCode).toBe(400)
  })

  it('rejects a malformed exchange body', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'POST', url: '/auth/exchange', payload: { code: 'x' } })

    expect(res.statusCode).toBe(400)
  })

  it('gives legacy sign-ins an expiring JWT too', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: `/auth/steam/callback?${assertion()}` })

    const token = new URL(res.headers.location as string).searchParams.get('token')!
    expect(decodeJwt(token).exp).toBeDefined()
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
