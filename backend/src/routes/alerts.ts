import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { authenticate } from '../middleware/authenticate'

const createSchema = z.object({
  skinId: z.string(),
  type: z.enum(['BUY_BELOW', 'SELL_ABOVE']),
  targetPrice: z.number().positive(),
})

const updateSchema = z.object({
  targetPrice: z.number().positive().optional(),
  isActive: z.boolean().optional(),
  type: z.enum(['BUY_BELOW', 'SELL_ABOVE']).optional(),
})

const FREE_ALERT_LIMIT = 1

const FREE_LIMIT_REACHED = {
  error: 'Free limit reached',
  message: `Free plan allows ${FREE_ALERT_LIMIT} alert. Upgrade to Premium for unlimited alerts.`,
  limit: FREE_ALERT_LIMIT,
}

export const alertRoutes: FastifyPluginAsync = async (app) => {
  app.get('/alerts', { onRequest: [authenticate] }, async (request) => {
    const { userId } = request.user

    const [alerts, user] = await Promise.all([
      prisma.alert.findMany({ where: { userId }, orderBy: { createdAt: 'desc' } }),
      prisma.user.findUnique({ where: { id: userId }, select: { isPremium: true } }),
    ])

    // Manual join (Alert has no `skin` relation in the Prisma schema)
    const skinIds = [...new Set(alerts.map((a) => a.skinId))]
    const skins = skinIds.length > 0
      ? await prisma.skin.findMany({
          where: { id: { in: skinIds } },
          include: { price: true },
        })
      : []
    const skinById = new Map(skins.map((s) => [s.id, s]))

    const isPremium = user?.isPremium ?? false
    const activeCount = alerts.filter((a) => a.isActive).length

    return {
      alerts: alerts.map((a) => {
        const skin = skinById.get(a.skinId)
        return {
          id: a.id,
          skinId: a.skinId,
          skinName: skin?.name ?? '',
          skinImageUrl: skin?.iconUrl ?? '',
          type: a.type,
          targetPrice: a.targetPrice,
          currentPrice: skin?.price?.lowestPrice ?? 0,
          isActive: a.isActive,
          isTriggered: a.isTriggered,
          createdAt: a.createdAt.toISOString(),
          triggeredAt: a.triggeredAt?.toISOString() ?? null,
        }
      }),
      meta: {
        activeCount,
        limit: isPremium ? null : FREE_ALERT_LIMIT,
        isPremium,
      },
    }
  })

  app.post('/alerts', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user

    const user = await prisma.user.findUnique({ where: { id: userId }, select: { isPremium: true } })

    // Free tier: enforce 1-alert limit
    if (!user?.isPremium) {
      const count = await prisma.alert.count({ where: { userId } })
      if (count >= FREE_ALERT_LIMIT) {
        return reply.status(403).send(FREE_LIMIT_REACHED)
      }
    }

    const body = createSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const alert = await prisma.alert.create({
      data: { userId, skinId: body.data.skinId, type: body.data.type, targetPrice: body.data.targetPrice },
    })

    return { ...alert, createdAt: alert.createdAt.toISOString(), triggeredAt: null }
  })

  app.put('/alerts/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }

    // Free users can manage (toggle/edit) their existing alert
    const body = updateSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const existing = await prisma.alert.findFirst({ where: { id, userId } })
    if (!existing) return reply.status(404).send({ error: 'Alert not found' })

    const keyFieldChanged = body.data.targetPrice !== undefined || body.data.type !== undefined
    const updated = await prisma.alert.update({
      where: { id },
      data: {
        targetPrice: body.data.targetPrice ?? existing.targetPrice,
        isActive: body.data.isActive ?? existing.isActive,
        type: body.data.type ?? existing.type,
        // Re-arm the alert if type or price changed so it can trigger again
        ...(keyFieldChanged ? { isTriggered: false, triggeredAt: null, isActive: true } : {}),
      },
    })

    return { ...updated, createdAt: updated.createdAt.toISOString(), triggeredAt: updated.triggeredAt?.toISOString() ?? null }
  })

  app.delete('/alerts/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }

    // Free users can delete their own alerts

    const existing = await prisma.alert.findFirst({ where: { id, userId } })
    if (!existing) return reply.status(404).send({ error: 'Alert not found' })

    await prisma.alert.delete({ where: { id } })
    return { success: true }
  })
}
