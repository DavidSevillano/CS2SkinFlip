import { FastifyPluginAsync } from 'fastify'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'

const priceService = new PriceService()

export const priceRoutes: FastifyPluginAsync = async (app) => {
  /**
   * GET /prices/batch?ids=id1,id2,...
   *
   * Returns prices for up to 50 skins in a single round-trip. Pure DB read —
   * the 2-hourly bulk job keeps every column fresh, so there are no external
   * API calls and no rate-limit concerns.
   */
  app.get('/prices/batch', async (request, reply) => {
    const { ids } = request.query as { ids?: string }
    if (!ids) return reply.status(400).send({ error: 'ids query param required' })

    const idList = ids.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 50)
    if (idList.length === 0) return {}

    const rows = await prisma.skinPrice.findMany({
      where: { skinId: { in: idList } },
    })

    const response: Record<string, object> = {}
    for (const row of rows) {
      response[row.skinId] = {
        skinportPrice:   row.skinportPrice,
        csgoMarketPrice: row.csgoMarketPrice,
        csdealsPrice:    row.csdealsPrice,
        dmarketPrice:    row.dmarketPrice,
        lowestPrice:     row.lowestPrice,
      }
    }
    return response
  })

  // Fetch (or refresh) live prices for a skin — cached for 5 min
  app.get('/prices/:skinId', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    return priceService.getPricesForSkin(skinId, skin.marketHashName)
  })

  // Force-refresh prices for a skin (busts cache)
  app.post('/prices/:skinId/refresh', async (request, reply) => {
    const { skinId } = request.params as { skinId: string }

    const skin = await prisma.skin.findUnique({ where: { id: skinId } })
    if (!skin) return reply.status(404).send({ error: 'Skin not found' })

    return priceService.getPricesForSkin(skinId, skin.marketHashName)
  })
}
