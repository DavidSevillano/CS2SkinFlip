import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { authenticate } from '../middleware/authenticate'
import { verifyAndAcknowledgePurchase } from '../services/googlePlay'

const verifySchema = z.object({
  purchaseToken: z.string().min(1),
})

export const billingRoutes: FastifyPluginAsync = async (app) => {
  // Verifies a Google Play one-time purchase and unlocks unlimited alerts on success.
  app.post('/billing/verify-purchase', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user

    const body = verifySchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const result = await verifyAndAcknowledgePurchase(body.data.purchaseToken)
    if (!result.ok) {
      return reply.status(400).send({ error: 'Purchase verification failed', message: result.reason })
    }

    // The Android client attaches the user's ID as the purchase's obfuscated account ID
    // so a purchase token can't be replayed against a different account.
    if (result.obfuscatedAccountId && result.obfuscatedAccountId !== userId) {
      return reply.status(403).send({ error: 'Purchase does not belong to this account' })
    }

    const user = await prisma.user.update({
      where: { id: userId },
      data: { isPremium: true },
      select: { isPremium: true, premiumUntil: true },
    })

    return { isPremium: user.isPremium, premiumUntil: user.premiumUntil?.toISOString() ?? null }
  })
}
