import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import { listVoidedPurchaseTokens } from '../services/googlePlay'
import type { FastifyBaseLogger } from 'fastify'

// Where the last successful check reached (epoch ms). `pipeline:` rather than
// `prices:` because app.ts wipes `prices:*` on every boot.
export const VOIDED_CHECKPOINT_KEY = 'pipeline:billing:voidedCheckedUntil'

// Called from every 6h price run, but refunds are rare and Play's voided list
// is cheap to page through only once a day.
const MIN_INTERVAL_MS = 24 * 60 * 60 * 1000

// Play rejects a startTime older than 30 days; a minute of margin keeps a
// clamped request from being refused for crossing the line in transit.
const MAX_LOOKBACK_MS = 30 * 24 * 60 * 60 * 1000 - 60 * 1000

// Re-read the tail of the previous window: a purchase voided just before the
// last check may only have appeared in Play's list afterwards. Revoking twice is
// harmless — already revoked rows are filtered out.
const OVERLAP_MS = 60 * 60 * 1000

/**
 * Takes premium away from accounts whose purchase Play has voided (refund,
 * chargeback, cancellation). Without this, buying and refunding kept premium
 * forever.
 *
 * Only purchases recorded in `Purchase` can be traced. Accounts unlocked before
 * that table existed get a row the next time the app re-verifies their purchase
 * on startup.
 *
 * Returns null when skipped (ran less than a day ago, or Play isn't configured).
 * Throws on a Play error without moving the checkpoint, so the next run retries
 * the same window.
 */
export async function revokeVoidedPurchases(log: FastifyBaseLogger): Promise<{ revoked: number } | null> {
  const now = Date.now()
  const raw = await redis.get(VOIDED_CHECKPOINT_KEY)
  const last = typeof raw === 'string' ? Number(raw) : typeof raw === 'number' ? raw : null

  if (last !== null && Number.isFinite(last) && now - last < MIN_INTERVAL_MS) return null

  const startTime = Math.max(
    last !== null && Number.isFinite(last) ? last - OVERLAP_MS : 0,
    now - MAX_LOOKBACK_MS,
  )

  const tokens = await listVoidedPurchaseTokens(startTime)
  if (tokens === null) return null

  let revoked = 0
  if (tokens.length > 0) {
    const hits = await prisma.purchase.findMany({
      where: { purchaseToken: { in: tokens }, revokedAt: null },
      select: { id: true, userId: true },
    })

    if (hits.length > 0) {
      const revokedIds = hits.map((h) => h.id)
      await prisma.purchase.updateMany({
        where: { id: { in: revokedIds } },
        data: { revokedAt: new Date(now) },
      })

      // An account keeps premium if it still holds another live purchase.
      const userIds = [...new Set(hits.map((h) => h.userId))]
      const stillEntitled = await prisma.purchase.findMany({
        where: { userId: { in: userIds }, revokedAt: null, id: { notIn: revokedIds } },
        select: { id: true, userId: true },
      })
      const entitled = new Set(stillEntitled.map((p) => p.userId))
      const downgrade = userIds.filter((id) => !entitled.has(id))
      if (downgrade.length > 0) {
        await prisma.user.updateMany({ where: { id: { in: downgrade } }, data: { isPremium: false } })
      }

      revoked = hits.length
      log.warn(`[Billing] Revoked ${revoked} voided purchase(s); premium removed from ${downgrade.length} account(s)`)
    }
  }

  await redis.set(VOIDED_CHECKPOINT_KEY, now)
  return { revoked }
}
