import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fakeRedisStore } from '../test/fakeRedis'

const redis = fakeRedisStore()
vi.mock('../redis/client', () => ({ redis }))

const { purchaseFindMany, purchaseUpdateMany, userUpdateMany } = vi.hoisted(() => ({
  purchaseFindMany: vi.fn(),
  purchaseUpdateMany: vi.fn(),
  userUpdateMany: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: {
    purchase: { findMany: purchaseFindMany, updateMany: purchaseUpdateMany },
    user: { updateMany: userUpdateMany },
  },
}))

const { listVoidedPurchaseTokens } = vi.hoisted(() => ({ listVoidedPurchaseTokens: vi.fn() }))
vi.mock('../services/googlePlay', () => ({ listVoidedPurchaseTokens }))

const { revokeVoidedPurchases, VOIDED_CHECKPOINT_KEY } = await import('./revokeVoidedPurchases')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any
const DAY = 24 * 60 * 60 * 1000
const NOW = new Date('2026-10-11T12:00:00.000Z').getTime()

/** Purchases as stored: `revoked` ones already have revokedAt set. */
function givenPurchases(rows: Array<{ id: string; userId: string; token: string; revoked?: boolean }>) {
  purchaseFindMany.mockImplementation(async ({ where }) => {
    return rows
      .filter((r) => !r.revoked)
      .filter((r) => (where.purchaseToken ? where.purchaseToken.in.includes(r.token) : true))
      .filter((r) => (where.userId ? where.userId.in.includes(r.userId) : true))
      .filter((r) => (where.id?.notIn ? !where.id.notIn.includes(r.id) : true))
      .map((r) => ({ id: r.id, userId: r.userId }))
  })
}

describe('revokeVoidedPurchases', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
    redis.store.clear()
    listVoidedPurchaseTokens.mockResolvedValue([])
    givenPurchases([])
  })

  it('revokes a refunded purchase and takes premium away from its account', async () => {
    listVoidedPurchaseTokens.mockResolvedValue(['refunded-token'])
    givenPurchases([{ id: 'p1', userId: 'user-1', token: 'refunded-token' }])

    const result = await revokeVoidedPurchases(log)

    expect(result).toEqual({ revoked: 1 })
    expect(purchaseUpdateMany).toHaveBeenCalledWith({
      where: { id: { in: ['p1'] } },
      data: { revokedAt: new Date(NOW) },
    })
    expect(userUpdateMany).toHaveBeenCalledWith({
      where: { id: { in: ['user-1'] } },
      data: { isPremium: false },
    })
  })

  it('keeps premium for an account that still holds another live purchase', async () => {
    listVoidedPurchaseTokens.mockResolvedValue(['refunded-token'])
    givenPurchases([
      { id: 'p1', userId: 'user-1', token: 'refunded-token' },
      { id: 'p2', userId: 'user-1', token: 'second-token' },
    ])

    await revokeVoidedPurchases(log)

    expect(purchaseUpdateMany).toHaveBeenCalled()
    expect(userUpdateMany).not.toHaveBeenCalled()
  })

  it('ignores voided tokens it never recorded', async () => {
    listVoidedPurchaseTokens.mockResolvedValue(['unknown-token'])

    const result = await revokeVoidedPurchases(log)

    expect(result).toEqual({ revoked: 0 })
    expect(purchaseUpdateMany).not.toHaveBeenCalled()
    expect(userUpdateMany).not.toHaveBeenCalled()
  })

  it('runs at most once a day, since it is called on every 6h price run', async () => {
    redis.store.set(VOIDED_CHECKPOINT_KEY, NOW - 6 * 60 * 60 * 1000)

    const result = await revokeVoidedPurchases(log)

    expect(result).toBeNull()
    expect(listVoidedPurchaseTokens).not.toHaveBeenCalled()
  })

  it('resumes from the last checkpoint, with an hour of overlap', async () => {
    redis.store.set(VOIDED_CHECKPOINT_KEY, NOW - 2 * DAY)

    await revokeVoidedPurchases(log)

    expect(listVoidedPurchaseTokens).toHaveBeenCalledWith(NOW - 2 * DAY - 60 * 60 * 1000)
    expect(redis.store.get(VOIDED_CHECKPOINT_KEY)).toBe(NOW)
  })

  // Play rejects a startTime older than 30 days. A first run, or one after a long
  // outage, has to clamp rather than fail forever.
  it('never asks Play to look back further than its 30-day limit', async () => {
    redis.store.set(VOIDED_CHECKPOINT_KEY, NOW - 90 * DAY)

    await revokeVoidedPurchases(log)

    const startTime = listVoidedPurchaseTokens.mock.calls[0][0]
    expect(startTime).toBeGreaterThan(NOW - 30 * DAY)
    expect(startTime).toBeLessThanOrEqual(NOW - 29 * DAY)
  })

  it('leaves the checkpoint alone when Play fails, so the window is retried', async () => {
    redis.store.set(VOIDED_CHECKPOINT_KEY, NOW - 2 * DAY)
    listVoidedPurchaseTokens.mockRejectedValue(new Error('quota'))

    await expect(revokeVoidedPurchases(log)).rejects.toThrow('quota')

    expect(redis.store.get(VOIDED_CHECKPOINT_KEY)).toBe(NOW - 2 * DAY)
  })

  it('does nothing when Google Play is not configured', async () => {
    listVoidedPurchaseTokens.mockResolvedValue(null)

    const result = await revokeVoidedPurchases(log)

    expect(result).toBeNull()
    expect(redis.store.has(VOIDED_CHECKPOINT_KEY)).toBe(false)
  })
})
