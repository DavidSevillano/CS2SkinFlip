import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { authenticate } from '../middleware/authenticate'

const upsertSchema = z.object({
  skinId: z.string(),
  targetBuyPrice: z.number().positive().nullable().optional(),
  targetSellPrice: z.number().positive().nullable().optional(),
  alertEnabled: z.boolean().optional(),
})

const updateSchema = z.object({
  targetBuyPrice: z.number().positive().nullable().optional(),
  targetSellPrice: z.number().positive().nullable().optional(),
  alertEnabled: z.boolean().optional(),
})

export const watchlistRoutes: FastifyPluginAsync = async (app) => {
  app.get('/watchlist', { onRequest: [authenticate] }, async (request) => {
    const { userId } = request.user

    const items = await prisma.watchlistItem.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
    })

    const skins = await prisma.skin.findMany({
      where: { id: { in: items.map((i) => i.skinId) } },
      include: { price: true },
    })
    const skinMap = new Map(skins.map((s) => [s.id, s]))

    return items.map((item) => {
      const skin = skinMap.get(item.skinId)
      return {
        ...item,
        createdAt: item.createdAt.toISOString(),
        updatedAt: item.updatedAt.toISOString(),
        skin: skin
          ? { name: skin.name, iconUrl: skin.iconUrl, rarity: skin.rarity, lowestPrice: skin.price?.lowestPrice ?? null }
          : null,
      }
    })
  })

  app.post('/watchlist', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const body = upsertSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const { skinId, targetBuyPrice, targetSellPrice, alertEnabled } = body.data

    const item = await prisma.watchlistItem.upsert({
      where: { userId_skinId: { userId, skinId } },
      update: {
        targetBuyPrice: targetBuyPrice ?? null,
        targetSellPrice: targetSellPrice ?? null,
        alertEnabled: alertEnabled ?? false,
      },
      create: {
        userId,
        skinId,
        targetBuyPrice: targetBuyPrice ?? null,
        targetSellPrice: targetSellPrice ?? null,
        alertEnabled: alertEnabled ?? false,
      },
    })

    return item
  })

  app.put('/watchlist/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }
    const body = updateSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const existing = await prisma.watchlistItem.findFirst({ where: { id, userId } })
    if (!existing) return reply.status(404).send({ error: 'Item not found' })

    const updated = await prisma.watchlistItem.update({
      where: { id },
      data: {
        targetBuyPrice: body.data.targetBuyPrice ?? existing.targetBuyPrice,
        targetSellPrice: body.data.targetSellPrice ?? existing.targetSellPrice,
        alertEnabled: body.data.alertEnabled ?? existing.alertEnabled,
      },
    })

    return updated
  })

  app.delete('/watchlist/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }

    const existing = await prisma.watchlistItem.findFirst({ where: { id, userId } })
    if (!existing) return reply.status(404).send({ error: 'Item not found' })

    await prisma.watchlistItem.delete({ where: { id } })
    return { success: true }
  })
}
