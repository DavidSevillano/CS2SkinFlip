import { FastifyPluginAsync } from 'fastify'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'

const priceService = new PriceService()

export const priceRoutes: FastifyPluginAsync = async (app) => {
  // Fetch (or refresh) live prices for a skin — cached for 5 min
  app.get('/prices/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    const prices = await priceService.getPricesForSkin(skinId, skin.marketHashName)
    return prices
  })

  // Force-refresh prices for a skin (busts cache)
  app.post('/prices/:skinId/refresh', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    // Delegate to service — it will re-fetch and re-cache
    const prices = await priceService.getPricesForSkin(skinId, skin.marketHashName)
    return prices
  })
}
