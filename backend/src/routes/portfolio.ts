import { FastifyPluginAsync } from 'fastify'
import { z } from 'zod'
import { prisma } from '../db/prisma'
import { isForeignKeyViolation } from '../db/errors'
import { authenticate } from '../middleware/authenticate'

const addItemSchema = z.object({
  skinId: z.string(),
  assetId: z.string(),
  acquirePrice: z.number().positive(),
  acquiredAt: z.string().datetime().optional(),
  float: z.number().min(0).max(1).optional(),
})

const syncRequestSchema = z.object({
  // CS2 caps an inventory at 1 000 items; 5 000 leaves room for anything Steam
  // counts that we don't while bounding what one request can make us process.
  items: z
    .array(
      z.object({
        assetId: z.string().max(32),
        marketHashName: z.string().max(200),
      }),
    )
    .max(5000),
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

  // Sync portfolio from Steam inventory items the client already fetched
  // (the client fetches from Steam directly — see PortfolioRepository.kt on Android —
  // so this route never talks to Steam and can't be affected by Steam rate-limiting
  // our server's shared outbound IP).
  app.post('/portfolio/sync', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user

    const body = syncRequestSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const { items } = body.data

    // Upsert only items that are already in our skin catalogue
    const knownSkins = await prisma.skin.findMany({
      where: { marketHashName: { in: items.map((i) => i.marketHashName) } },
      include: { price: true },
    })
    const skinByName = new Map(knownSkins.map((s) => [s.marketHashName, s]))

    const now = new Date()
    const rows = items.flatMap((item) => {
      const skin = skinByName.get(item.marketHashName)
      if (!skin) return []
      return [{
        userId,
        skinId: skin.id,
        assetId: item.assetId,
        // Real purchase price is unknown — seed with the current market price so
        // P&L starts at 0 and reflects movement from the sync point forward,
        // rather than showing a meaningless -100% loss. User can correct it later.
        acquirePrice: skin.price?.lowestPrice ?? 0,
        acquiredAt: now,
      }]
    })

    // One statement instead of one upsert per item (hundreds of sequential round
    // trips for a large inventory). skipDuplicates keeps items already in the
    // portfolio — and any purchase price the user corrected — untouched.
    if (rows.length > 0) {
      await prisma.portfolioItem.createMany({ data: rows, skipDuplicates: true })
    }

    // Sold or traded items have left the inventory, so they leave the portfolio.
    // Not on an empty list, though: that is far likelier a Steam hiccup than a
    // user who sold everything, and wiping would lose their corrected prices.
    let removed = 0
    if (items.length > 0) {
      const result = await prisma.portfolioItem.deleteMany({
        where: { userId, assetId: { notIn: items.map((i) => i.assetId) } },
      })
      removed = result.count
    }

    return { synced: rows.length, total: items.length, removed }
  })

  app.post('/portfolio', { onRequest: [authenticate] }, async (request, reply) => {
    const { userId } = request.user
    const body = addItemSchema.safeParse(request.body)
    if (!body.success) return reply.status(400).send({ error: 'Invalid body', details: body.error.flatten() })

    const { skinId, assetId, acquirePrice, acquiredAt, float } = body.data

    try {
      return await prisma.portfolioItem.upsert({
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
    } catch (e) {
      if (isForeignKeyViolation(e)) return reply.status(404).send({ error: 'Skin not found' })
      throw e
    }
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
