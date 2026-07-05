import { FastifyPluginAsync } from 'fastify'
import axios from 'axios'
import bcrypt from 'bcrypt'
import crypto from 'crypto'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { env } from '../config/env'
import { authenticate } from '../middleware/authenticate'
import { sendPasswordResetEmail, sendVerificationEmail } from '../services/email'
import { checkRateLimit } from '../redis/client'

const STEAM_OPENID_ENDPOINT = 'https://steamcommunity.com/openid/login'
const STEAM_ID_REGEX = /^https:\/\/steamcommunity\.com\/openid\/id\/(\d{17})$/
const BCRYPT_ROUNDS = 10

/** Fingerprint of the current passwordHash — embedding it in a reset token makes the
 *  token single-use: it stops matching as soon as the password actually changes. */
function passwordFingerprint(passwordHash: string): string {
  return crypto.createHash('sha256').update(passwordHash).digest('hex').slice(0, 16)
}

const registerSchema = z.object({
  email: z.string().email().toLowerCase(),
  password: z.string().min(8).max(72),
  username: z.string().min(2).max(40).optional(),
})

const loginSchema = z.object({
  email: z.string().email().toLowerCase(),
  password: z.string().min(1),
})

function resetPasswordHtmlPage(opts: { token: string; invalidToken?: boolean }): string {
  const { token, invalidToken } = opts
  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Reset your password — CS2 SkinFlip</title>
<style>
  body { font-family: -apple-system, sans-serif; background: #121212; color: #eee; display: flex; justify-content: center; padding: 48px 16px; }
  .card { max-width: 360px; width: 100%; }
  h1 { font-size: 20px; }
  input { width: 100%; box-sizing: border-box; padding: 12px; margin: 8px 0; border-radius: 8px; border: 1px solid #444; background: #1e1e1e; color: #eee; }
  button { width: 100%; padding: 12px; border-radius: 8px; border: none; background: #ff6b35; color: #fff; font-weight: bold; cursor: pointer; }
  .msg { margin-top: 12px; font-size: 14px; }
  .error { color: #ff5c5c; }
  .success { color: #4caf50; }
</style></head>
<body><div class="card">
  <h1>Reset your password</h1>
  ${invalidToken
    ? `<p class="msg error">This link is invalid or has expired. Request a new one from the app.</p>`
    : `
      <form id="f">
        <input type="password" id="newPassword" placeholder="New password (min. 8 characters)" minlength="8" required />
        <input type="password" id="confirmPassword" placeholder="Confirm new password" minlength="8" required />
        <button type="submit">Update password</button>
      </form>
      <p class="msg error" id="err"></p>
      <script>
        document.getElementById('f').addEventListener('submit', async (e) => {
          e.preventDefault()
          const newPassword = document.getElementById('newPassword').value
          const confirmPassword = document.getElementById('confirmPassword').value
          const err = document.getElementById('err')
          if (newPassword !== confirmPassword) { err.textContent = "Passwords don't match"; return }
          const res = await fetch('/auth/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token: ${JSON.stringify(token)}, newPassword }),
          })
          if (res.ok) {
            document.querySelector('.card').innerHTML = '<h1>Reset your password</h1><p class="msg success">Your password has been changed. You can now sign in with it in the app.</p>'
          } else {
            const data = await res.json().catch(() => ({}))
            err.textContent = data.error || 'Could not reset password'
          }
        })
      </script>
    `}
</div></body></html>`
}

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
      .redirect(`${env.MOBILE_DEEP_LINK}?token=${token}`)
  })

  // ── Email / password registration ──────────────────────────────────────────
  app.post('/auth/register', async (request, reply) => {
    const body = registerSchema.safeParse(request.body)
    if (!body.success) {
      return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })
    }
    const { email, password, username } = body.data

    const existing = await prisma.user.findUnique({ where: { email } })
    if (existing) {
      return reply.status(409).send({ error: 'Email already registered' })
    }

    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS)
    const user = await prisma.user.create({
      data: {
        email,
        passwordHash,
        username: username ?? email.split('@')[0],
      },
    })

    const verifyToken = app.jwt.sign(
      { userId: user.id, steamId: null, purpose: 'verify_email' },
      { expiresIn: '24h' },
    )
    await sendVerificationEmail(email, `${env.PUBLIC_URL}/auth/verify-email?token=${verifyToken}`)

    const token = app.jwt.sign({ userId: user.id, steamId: user.steamId ?? null })

    return reply
      .setCookie('token', token, {
        httpOnly: true,
        secure: env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 7 * 24 * 60 * 60,
      })
      .send({
        token,
        user: {
          id: user.id,
          email: user.email,
          username: user.username,
          isPremium: user.isPremium,
        },
      })
  })

  // ── Email / password login ─────────────────────────────────────────────────
  app.post('/auth/login', async (request, reply) => {
    const body = loginSchema.safeParse(request.body)
    if (!body.success) {
      return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })
    }
    const { email, password } = body.data

    const user = await prisma.user.findUnique({ where: { email } })
    if (!user || !user.passwordHash) {
      return reply.status(401).send({ error: 'Invalid email or password' })
    }

    const valid = await bcrypt.compare(password, user.passwordHash)
    if (!valid) {
      return reply.status(401).send({ error: 'Invalid email or password' })
    }

    const token = app.jwt.sign({ userId: user.id, steamId: user.steamId ?? null })

    return reply
      .setCookie('token', token, {
        httpOnly: true,
        secure: env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 7 * 24 * 60 * 60,
      })
      .send({
        token,
        user: {
          id: user.id,
          email: user.email,
          username: user.username,
          isPremium: user.isPremium,
        },
      })
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
      email: user.email,
      username: user.username,
      avatarUrl: user.avatarUrl,
      isPremium: user.isPremium,
      premiumUntil: user.premiumUntil?.toISOString() ?? null,
      emailVerified: user.emailVerified,
    }
  })

  /** Store or refresh the FCM device token for push notifications */
  app.put('/auth/me/fcm-token', { onRequest: [authenticate] }, async (request, reply) => {
    const body = z.object({ token: z.string().min(1) }).safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'token is required' })

    const { userId } = request.user
    await prisma.user.update({
      where: { id: userId },
      data: { fcmToken: body.data.token },
    })
    return { ok: true }
  })

  // ── Forgot password: request a reset email ─────────────────────────────────
  app.post('/auth/forgot-password', async (request, reply) => {
    const body = z.object({ email: z.string().email().toLowerCase() }).safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    // 3 requests per 15 min per email — prevents spamming someone's inbox with reset links
    const allowed = await checkRateLimit(`forgot-password:${body.data.email}`, 3, 15 * 60)
    if (!allowed) return reply.status(429).send({ error: 'Too many requests. Try again later.' })

    const user = await prisma.user.findUnique({ where: { email: body.data.email } })
    // Always respond the same way whether or not the account exists — avoids leaking who has an account
    if (user?.passwordHash) {
      const resetToken = app.jwt.sign(
        { userId: user.id, steamId: null, purpose: 'password_reset', pwv: passwordFingerprint(user.passwordHash) },
        { expiresIn: '15m' },
      )
      await sendPasswordResetEmail(body.data.email, `${env.PUBLIC_URL}/auth/reset-password?token=${resetToken}`)
    }
    return { ok: true, message: 'If that email is registered, a reset link has been sent.' }
  })

  // ── Reset password: emailed link opens this page, form POSTs back below ────
  app.get('/auth/reset-password', async (request, reply) => {
    const { token } = request.query as { token?: string }
    if (!token) return reply.status(400).type('text/html').send(resetPasswordHtmlPage({ token: '', invalidToken: true }))

    try {
      const payload = app.jwt.verify<{ userId: string; purpose?: string; pwv?: string }>(token)
      if (payload.purpose !== 'password_reset') throw new Error('wrong purpose')
      const user = await prisma.user.findUnique({ where: { id: payload.userId } })
      if (!user?.passwordHash || passwordFingerprint(user.passwordHash) !== payload.pwv) throw new Error('already used')
    } catch {
      return reply.status(400).type('text/html').send(resetPasswordHtmlPage({ token: '', invalidToken: true }))
    }

    return reply.type('text/html').send(resetPasswordHtmlPage({ token }))
  })

  app.post('/auth/reset-password', async (request, reply) => {
    const body = z.object({
      token: z.string().min(1),
      newPassword: z.string().min(8).max(72),
    }).safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    let userId: string
    try {
      const payload = app.jwt.verify<{ userId: string; purpose?: string; pwv?: string }>(body.data.token)
      if (payload.purpose !== 'password_reset') throw new Error('wrong purpose')
      const user = await prisma.user.findUnique({ where: { id: payload.userId } })
      // pwv mismatch means either the password already changed (token already used) or is stale
      if (!user?.passwordHash || passwordFingerprint(user.passwordHash) !== payload.pwv) throw new Error('already used')
      userId = payload.userId
    } catch {
      return reply.status(400).send({ error: 'This link is invalid or has expired' })
    }

    const passwordHash = await bcrypt.hash(body.data.newPassword, BCRYPT_ROUNDS)
    await prisma.user.update({ where: { id: userId }, data: { passwordHash } })
    return { ok: true }
  })

  // ── Email verification ──────────────────────────────────────────────────────
  app.get('/auth/verify-email', async (request, reply) => {
    const { token } = request.query as { token?: string }
    const fail = () => reply.redirect(`${env.MOBILE_DEEP_LINK}?verified=false`)
    if (!token) return fail()

    try {
      const payload = app.jwt.verify<{ userId: string; purpose?: string }>(token)
      if (payload.purpose !== 'verify_email') throw new Error('wrong purpose')
      await prisma.user.update({ where: { id: payload.userId }, data: { emailVerified: true } })
    } catch {
      return fail()
    }

    return reply.redirect(`${env.MOBILE_DEEP_LINK}?verified=true`)
  })

  app.post('/auth/resend-verification', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const user = await prisma.user.findUnique({ where: { id: userId } })
    if (!user || !user.email) return reply.status(400).send({ error: 'This account has no email to verify' })
    if (user.emailVerified) return { ok: true, alreadyVerified: true }

    // 3 requests per 15 min per account
    const allowed = await checkRateLimit(`resend-verification:${userId}`, 3, 15 * 60)
    if (!allowed) return reply.status(429).send({ error: 'Too many requests. Try again later.' })

    const verifyToken = app.jwt.sign(
      { userId: user.id, steamId: null, purpose: 'verify_email' },
      { expiresIn: '24h' },
    )
    await sendVerificationEmail(user.email, `${env.PUBLIC_URL}/auth/verify-email?token=${verifyToken}`)
    return { ok: true, alreadyVerified: false }
  })
}
