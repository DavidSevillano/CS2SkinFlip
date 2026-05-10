import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { SteamService } from '../services/steam'
import { authenticate } from '../middleware/authenticate'

const steam = new SteamService()

const addItemSchema = z.object({
  skinId: z.string(),
  assetId: z.string(),
  acquirePrice: z.number().positive(),
  acquiredAt: z.string().datetime().optional(),
  float: z.number().min(0).max(1).optional(),
})

export const portfolioRoutes: FastifyPluginAsync = async (app) => {
  app.get('/portfolio', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user

    const items = await prisma.portfolioItem.findMany({
      where: { userId },
      orderBy: { acquiredAt: 'desc' },
    })

    const skins = await prisma.skin.findMany({
      where: { id: { in: items.map((i) => i.skinId) } },
      include: { price: true },
    })

    const skinMap = new Map(skins.map((s) => [s.id, s]))

    const enriched = items.map((item) => {
      const skin = skinMap.get(item.skinId)
      const currentPrice = skin?.price?.lowestPrice ?? null
      const profitLoss = currentPrice !== null ? currentPrice - item.acquirePrice : null
      const profitLossPct =
        profitLoss !== null ? (profitLoss / item.acquirePrice) * 100 : null

      return {
        ...item,
        acquiredAt: item.acquiredAt.toISOString(),
        createdAt: item.createdAt.toISOString(),
        skin: skin ? { name: skin.name, iconUrl: skin.iconUrl, rarity: skin.rarity } : null,
        currentPrice,
        profitLoss,
        profitLossPct,
      }
    })

    const totalValue = enriched.reduce((acc, i) => acc + (i.currentPrice ?? i.acquirePrice), 0)
    const totalInvested = enriched.reduce((acc, i) => acc + i.acquirePrice, 0)

    return {
      items: enriched,
      summary: {
        totalValue,
        totalInvested,
        totalProfitLoss: totalValue - totalInvested,
        totalProfitLossPct: totalInvested > 0 ? ((totalValue - totalInvested) / totalInvested) * 100 : 0,
        itemCount: enriched.length,
      },
    }
  })

  // Sync portfolio from live Steam inventory
  app.post('/portfolio/sync', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId, steamId } = request.user

    if (!steamId) {
      return reply.status(400).send({ error: 'Steam account required to sync inventory' })
    }

    const inventoryItems = await steam.getInventory(steamId)

    // Upsert only items that are already in our skin catalogue
    const knownSkins = await prisma.skin.findMany({
      where: { marketHashName: { in: inventoryItems.map((i) => i.marketHashName) } },
    })
    const skinByName = new Map(knownSkins.map((s) => [s.marketHashName, s]))

    let synced = 0
    for (const item of inventoryItems) {
      const skin = skinByName.get(item.marketHashName)
      if (!skin) continue

      await prisma.portfolioItem.upsert({
        where: { userId_assetId: { userId, assetId: item.assetId } },
        update: {},
        create: {
          userId,
          skinId: skin.id,
          assetId: item.assetId,
          acquirePrice: 0, // unknown — user can update manually
          acquiredAt: new Date(),
        },
      })
      synced++
    }

    return { synced, total: inventoryItems.length }
  })

  app.post('/portfolio', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const body = addItemSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const { skinId, assetId, acquirePrice, acquiredAt, float } = body.data

    const item = await prisma.portfolioItem.upsert({
      where: { userId_assetId: { userId, assetId } },
      update: { acquirePrice, float: float ?? null },
      create: {
        userId,
        skinId,
        assetId,
        acquirePrice,
        acquiredAt: acquiredAt ? new Date(acquiredAt) : new Date(),
        float: float ?? null,
      },
    })

    return item
  })

  app.delete('/portfolio/:id', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const { id } = request.params as { id: string }

    const item = await prisma.portfolioItem.findFirst({ where: { id, userId } })
    if (!item) return reply.status(404).send({ error: 'Item not found' })

    await prisma.portfolioItem.delete({ where: { id } })
    return { success: true }
  })
}
