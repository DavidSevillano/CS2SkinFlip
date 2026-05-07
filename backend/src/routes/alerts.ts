import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { authenticate } from '../middleware/authenticate'

const FREE_ALERT_LIMIT = 5

const createSchema = z.object({
  skinId: z.string(),
  type: z.enum(['BUY_BELOW', 'SELL_ABOVE']),
  targetPrice: z.number().positive(),
})

const updateSchema = z.object({
  targetPrice: z.number().positive().optional(),
  isActive: z.boolean().optional(),
})

export const alertRoutes: FastifyPluginAsync = async (app) => {
  app.get('/alerts', { onRequest: [authenticate] }, async (request) => {
    const { userId } = request.user

    const [alerts, user] = await Promise.all([
      prisma.alert.findMany({ where: { userId }, orderBy: { createdAt: 'desc' } }),
      prisma.user.findUnique({ where: { id: userId }, select: { isPremium: true } }),
    ])

    const activeCount = alerts.filter((a) => a.isActive).length

    return {
      alerts: alerts.map((a) => ({ ...a, createdAt: a.createdAt.toISOString(), triggeredAt: a.triggeredAt?.toISOString() ?? null })),
      meta: {
        activeCount,
        limit: user?.isPremium ? null : FREE_ALERT_LIMIT,
        isPremium: user?.isPremium ?? false,
      },
    }
  })

  app.post('/alerts', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const body = createSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const user = await prisma.user.findUnique({ where: { id: userId }, select: { isPremium: true } })

    if (!user?.isPremium) {
      const activeCount = await prisma.alert.count({ where: { userId, isActive: true } })
      if (activeCount >= FREE_ALERT_LIMIT) {
        return reply.status(403).send({
          error: 'Free plan limit reached',
          message: `Free plan allows up to ${FREE_ALERT_LIMIT} active alerts. Upgrade to Premium for unlimited alerts.`,
          limit: FREE_ALERT_LIMIT,
          current: activeCount,
        })
      }
    }

    const alert = await prisma.alert.create({
      data: { userId, skinId: body.data.skinId, type: body.data.type, targetPrice: body.data.targetPrice },
    })

    return { ...alert, createdAt: alert.createdAt.toISOString(), triggeredAt: null }
  })

  app.put('/alerts/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }
    const body = updateSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const existing = await prisma.alert.findFirst({ where: { id, userId } })
    if (!existing) return reply.status(404).send({ error: 'Alert not found' })

    const updated = await prisma.alert.update({
      where: { id },
      data: {
        targetPrice: body.data.targetPrice ?? existing.targetPrice,
        isActive: body.data.isActive ?? existing.isActive,
      },
    })

    return { ...updated, createdAt: updated.createdAt.toISOString(), triggeredAt: updated.triggeredAt?.toISOString() ?? null }
  })

  app.delete('/alerts/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }

    const existing = await prisma.alert.findFirst({ where: { id, userId } })
    if (!existing) return reply.status(404).send({ error: 'Alert not found' })

    await prisma.alert.delete({ where: { id } })
    return { success: true }
  })
}
