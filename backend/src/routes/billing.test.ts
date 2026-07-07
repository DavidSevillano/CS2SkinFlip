import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'
import fastifyJwt from '@fastify/jwt'

const { userFindUnique, userUpdate } = vi.hoisted(() => ({
  userFindUnique: vi.fn(),
  userUpdate: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: { user: { findUnique: userFindUnique, update: userUpdate } },
}))

const { verifyAndAcknowledgePurchase } = vi.hoisted(() => ({ verifyAndAcknowledgePurchase: vi.fn() }))
vi.mock('../services/googlePlay', () => ({ verifyAndAcknowledgePurchase }))

const { billingRoutes } = await import('./billing')

async function buildTestApp() {
  const app = Fastify()
  await app.register(fastifyJwt, { secret: 'test-secret-at-least-32-characters-long' })
  await app.register(billingRoutes)
  return app
}

describe('POST /billing/verify-purchase', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userFindUnique.mockResolvedValue({ id: 'user-1' })
  })

  it('rejects requests without a valid JWT', async () => {
    const app = await buildTestApp()

    const res = await app.inject({ method: 'POST', url: '/billing/verify-purchase', payload: { purchaseToken: 't' } })

    expect(res.statusCode).toBe(401)
  })

  it('rejects a missing purchaseToken', async () => {
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1' })

    const res = await app.inject({
      method: 'POST',
      url: '/billing/verify-purchase',
      headers: { authorization: `Bearer ${token}` },
      payload: {},
    })

    expect(res.statusCode).toBe(400)
    expect(verifyAndAcknowledgePurchase).not.toHaveBeenCalled()
  })

  it('returns 400 when Google Play verification fails', async () => {
    verifyAndAcknowledgePurchase.mockResolvedValue({ ok: false, reason: 'Purchase is not in a completed state' })
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1' })

    const res = await app.inject({
      method: 'POST',
      url: '/billing/verify-purchase',
      headers: { authorization: `Bearer ${token}` },
      payload: { purchaseToken: 'tok' },
    })

    expect(res.statusCode).toBe(400)
    expect(userUpdate).not.toHaveBeenCalled()
  })

  it('rejects a purchase whose obfuscated account id belongs to a different user', async () => {
    verifyAndAcknowledgePurchase.mockResolvedValue({ ok: true, obfuscatedAccountId: 'someone-else' })
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1' })

    const res = await app.inject({
      method: 'POST',
      url: '/billing/verify-purchase',
      headers: { authorization: `Bearer ${token}` },
      payload: { purchaseToken: 'tok' },
    })

    expect(res.statusCode).toBe(403)
    expect(userUpdate).not.toHaveBeenCalled()
  })

  it('unlocks premium when the purchase is valid and belongs to the caller', async () => {
    verifyAndAcknowledgePurchase.mockResolvedValue({ ok: true, obfuscatedAccountId: 'user-1' })
    userUpdate.mockResolvedValue({ isPremium: true, premiumUntil: null })
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1' })

    const res = await app.inject({
      method: 'POST',
      url: '/billing/verify-purchase',
      headers: { authorization: `Bearer ${token}` },
      payload: { purchaseToken: 'tok' },
    })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ isPremium: true, premiumUntil: null })
    expect(userUpdate).toHaveBeenCalledWith({
      where: { id: 'user-1' },
      data: { isPremium: true },
      select: { isPremium: true, premiumUntil: true },
    })
  })

  it('allows the purchase through when Play recorded no obfuscated account id', async () => {
    verifyAndAcknowledgePurchase.mockResolvedValue({ ok: true, obfuscatedAccountId: null })
    userUpdate.mockResolvedValue({ isPremium: true, premiumUntil: null })
    const app = await buildTestApp()
    const token = app.jwt.sign({ userId: 'user-1' })

    const res = await app.inject({
      method: 'POST',
      url: '/billing/verify-purchase',
      headers: { authorization: `Bearer ${token}` },
      payload: { purchaseToken: 'tok' },
    })

    expect(res.statusCode).toBe(200)
  })
})
