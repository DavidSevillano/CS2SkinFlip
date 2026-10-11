import { FastifyPluginAsync } from 'fastify'
import axios from 'axios'
import { randomBytes, timingSafeEqual } from 'crypto'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { env } from '../config/env'
import { authenticate } from '../middleware/authenticate'
import { redis } from '../redis/client'
import {
  AUTH_CODE_KEY_PREFIX,
  AUTH_CODE_TTL_SECONDS,
  CHALLENGE_PATTERN,
  TOKEN_TTL,
  VERIFIER_PATTERN,
  challengeFor,
} from '../config/auth'
import { normalizeLocale } from '../services/alertMessages'

interface PendingLogin {
  userId: string
  steamId: string
  challenge: string
}

const exchangeSchema = z.object({
  code: z.string().min(1).max(200),
  verifier: z.string().regex(VERIFIER_PATTERN),
})

function matchesChallenge(verifier: string, challenge: string): boolean {
  const actual = Buffer.from(challengeFor(verifier))
  const expected = Buffer.from(challenge)
  return actual.length === expected.length && timingSafeEqual(actual, expected)
}

const STEAM_OPENID_ENDPOINT ='https://steamcommunity.com/openid/login'
const STEAM_ID_REGEX = /^https:\/\/steamcommunity\.com\/openid\/id\/(\d{17})$/

/**
 * `challenge` (PKCE, from current app versions) rides on return_to. Steam signs
 * return_to, so the callback can trust the challenge it reads back from there —
 * unlike a bare query parameter, which anyone holding the URL could rewrite.
 */
function buildSteamLoginUrl(challenge?: string): string {
  const returnTo = new URL(env.STEAM_CALLBACK_URL)
  if (challenge) returnTo.searchParams.set('challenge', challenge)
  const params = new URLSearchParams({
    'openid.ns': 'http://specs.openid.net/auth/2.0',
    'openid.mode': 'checkid_setup',
    'openid.return_to': returnTo.toString(),
    'openid.realm': env.FRONTEND_URL,
    'openid.identity': 'http://specs.openid.net/auth/2.0/identifier_select',
    'openid.claimed_id': 'http://specs.openid.net/auth/2.0/identifier_select',
  })
  return `${STEAM_OPENID_ENDPOINT}?${params.toString()}`
}

/**
 * True if `returnTo` is our own callback — same origin and path, any query.
 *
 * Steam's `check_authentication` only vouches for its signature and nonce, not
 * for who the assertion was minted for. Any other site offering "Sign in through
 * Steam" collects equally valid assertions from its visitors, and without this
 * check could replay one here and be handed that visitor's account. A plain
 * prefix test is not enough either: `/auth/steam/callback.evil` starts with it.
 */
function isOurReturnTo(returnTo: string | undefined): boolean {
  if (!returnTo) return false
  try {
    const actual = new URL(returnTo)
    const expected = new URL(env.STEAM_CALLBACK_URL)
    return actual.origin === expected.origin && actual.pathname === expected.pathname
  } catch {
    return false
  }
}

async function verifySteamAssertion(query: Record<string, string>): Promise<string | null> {
  // Checked before asking Steam: an assertion meant for someone else, or from
  // another provider, is rejected without spending a round trip on it.
  if (query['openid.op_endpoint'] !== STEAM_OPENID_ENDPOINT) return null
  if (!isOurReturnTo(query['openid.return_to'])) return null

  // Only the openid.* fields: the callback URL also carries our own `challenge`.
  const openidFields = Object.fromEntries(Object.entries(query).filter(([key]) => key.startsWith('openid.')))
  const body = new URLSearchParams({ ...openidFields, 'openid.mode': 'check_authentication' })

  const { data } = await axios.post(STEAM_OPENID_ENDPOINT, body.toString(), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    timeout: 5000,
  })

  if (!String(data).includes('is_valid:true')) return null

  const claimedId = query['openid.claimed_id'] ?? query['openid.identity'] ?? ''
  const match = claimedId.match(STEAM_ID_REGEX)
  return match ? match[1] : null
}

export const authRoutes: FastifyPluginAsync = async (app) => {
  // Redirects browser to Steam login
  app.get('/auth/steam', async (request, reply) => {
    const { challenge } = request.query as { challenge?: string }
    if (challenge !== undefined && !CHALLENGE_PATTERN.test(challenge)) {
      return reply.status(400).send({ error: 'Invalid challenge' })
    }
    return reply.redirect(buildSteamLoginUrl(challenge))
  })

  // Steam redirects here after login; we verify & issue a JWT
  app.get('/auth/steam/callback', async (request, reply) => {
    const query = request.query as Record<string, string>

    let steamId: string | null = null
    try {
      steamId = await verifySteamAssertion(query)
    } catch {
      return reply.status(502).send({ error: 'Failed to verify Steam assertion' })
    }

    if (!steamId) {
      return reply.status(401).send({ error: 'Steam authentication failed' })
    }

    // Best-effort profile fetch — falls back to placeholder values on failure
    let username = `Player_${steamId.slice(-6)}`
    let avatarUrl: string | null = null

    try {
      const { data } = await axios.get(
        'https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/',
        { params: { key: env.STEAM_API_KEY, steamids: steamId }, timeout: 5000 },
      )
      const player = data.response?.players?.[0]
      if (player) {
        username = player.personaname
        avatarUrl = player.avatarfull ?? null
      }
    } catch {
      // Non-fatal — continue with placeholder
    }

    const user = await prisma.user.upsert({
      where: { steamId },
      update: { username, avatarUrl },
      create: { steamId, username, avatarUrl },
    })

    // Current app versions: a one-time code bound to their PKCE challenge, which
    // only the app holding the verifier can redeem at POST /auth/exchange. A JWT
    // on the deep link could be read by any app registering the same scheme, and
    // a link carrying someone else's JWT would sign the victim in to that account.
    const challenge = new URL(query['openid.return_to']).searchParams.get('challenge')
    if (challenge && CHALLENGE_PATTERN.test(challenge)) {
      const code = randomBytes(32).toString('base64url')
      await redis.set(
        `${AUTH_CODE_KEY_PREFIX}${code}`,
        { userId: user.id, steamId: user.steamId, challenge },
        { ex: AUTH_CODE_TTL_SECONDS },
      )
      return reply.redirect(`${env.MOBILE_DEEP_LINK}?code=${code}`)
    }

    // Legacy app versions send no challenge and read the JWT off the deep link.
    // Kept until they have aged out — see CLAUDE.md (Steam sign-in).
    const token = app.jwt.sign({ userId: user.id, steamId: user.steamId }, { expiresIn: TOKEN_TTL })

    // Mobile apps receive token via query param redirect; web apps can read the cookie
    return reply
      .setCookie('token', token, {
        httpOnly: true,
        secure: env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 7 * 24 * 60 * 60,
      })
      .redirect(`${env.MOBILE_DEEP_LINK}?token=${token}`)
  })

  /**
   * Redeems a one-time code from the PKCE callback. The code is consumed by the
   * first attempt whatever its outcome, so a wrong verifier can't be retried.
   */
  app.post('/auth/exchange', async (request, reply) => {
    const body = exchangeSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body' })

    const pending = await redis.getdel<PendingLogin>(`${AUTH_CODE_KEY_PREFIX}${body.data.code}`)
    if (!pending || !matchesChallenge(body.data.verifier, pending.challenge)) {
      return reply.status(400).send({ error: 'Invalid or expired code' })
    }

    const token = app.jwt.sign({ userId: pending.userId, steamId: pending.steamId }, { expiresIn: TOKEN_TTL })
    return { token }
  })

  app.post('/auth/logout', async (_request, reply) => {
    return reply.clearCookie('token', { path: '/' }).send({ success: true })
  })

  /** Permanently deletes the caller's account and all associated data (cascades via schema). */
  app.delete('/auth/me', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    await prisma.user.delete({ where: { id: userId } })
    return reply.clearCookie('token', { path: '/' }).send({ success: true })
  })

  app.get('/auth/me', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const user = await prisma.user.findUnique({ where: { id: userId } })
    if (!user) return reply.status(404).send({ error: 'User not found' })

    return {
      id: user.id,
      steamId: user.steamId,
      username: user.username,
      avatarUrl: user.avatarUrl,
      isPremium: user.isPremium,
      premiumUntil: user.premiumUntil?.toISOString() ?? null,
    }
  })

  /** Store or refresh the FCM device token for push notifications */
  app.put('/auth/me/fcm-token', { onRequest: [authenticate] }, async (request, reply) => {
    // `locale` is the app language, for writing alert notifications in it. App
    // versions that predate it send none; that leaves the stored one alone.
    const body = z.object({
      token: z.string().min(1),
      locale: z.string().max(35).optional(),
    }).safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'token is required' })

    const { userId } = request.user
    await prisma.user.update({
      where: { id: userId },
      data: {
        fcmToken: body.data.token,
        ...(body.data.locale !== undefined && { locale: normalizeLocale(body.data.locale) }),
      },
    })
    return { ok: true }
  })
}
