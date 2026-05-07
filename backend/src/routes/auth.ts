import { FastifyPluginAsync } from 'fastify'
import axios from 'axios'
import { prisma } from '../db/prisma'
import { env } from '../config/env'
import { authenticate } from '../middleware/authenticate'

const STEAM_OPENID_ENDPOINT = 'https://steamcommunity.com/openid/login'
const STEAM_ID_REGEX = /^https:\/\/steamcommunity\.com\/openid\/id\/(\d{17})$/

function buildSteamLoginUrl(): string {
  const params = new URLSearchParams({
    'openid.ns': 'http://specs.openid.net/auth/2.0',
    'openid.mode': 'checkid_setup',
    'openid.return_to': env.STEAM_CALLBACK_URL,
    'openid.realm': env.FRONTEND_URL,
    'openid.identity': 'http://specs.openid.net/auth/2.0/identifier_select',
    'openid.claimed_id': 'http://specs.openid.net/auth/2.0/identifier_select',
  })
  return `${STEAM_OPENID_ENDPOINT}?${params.toString()}`
}

async function verifySteamAssertion(query: Record<string, string>): Promise<string | null> {
  const body = new URLSearchParams({ ...query, 'openid.mode': 'check_authentication' })

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
  app.get('/auth/steam', async (_request, reply) => {
    return reply.redirect(buildSteamLoginUrl())
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

    const token = app.jwt.sign({ userId: user.id, steamId: user.steamId })

    // Mobile apps receive token via query param redirect; web apps can read the cookie
    return reply
      .setCookie('token', token, {
        httpOnly: true,
        secure: env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 7 * 24 * 60 * 60,
      })
      .redirect(`${env.FRONTEND_URL}/auth/success?token=${token}`)
  })

  app.post('/auth/logout', async (_request, reply) => {
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
}
