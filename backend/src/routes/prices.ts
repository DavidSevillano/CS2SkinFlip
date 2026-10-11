import { FastifyPluginAsync } from 'fastify'
import { prisma } from '../db/prisma'
import { PriceService } from '../services/prices'
import { change24h, fetchReferencePrices } from '../services/priceReference'

const priceService = new PriceService()

export const priceRoutes: FastifyPluginAsync = async (app) => {
  /**
   * GET /prices/batch?ids=id1,id2,...
   *
   * Current prices and 24h change for up to 50 skins in one round trip — what
   * the watchlist refreshes with. Pure DB read; the 6h bulk job keeps every
   * column fresh, so there are no external API calls.
   */
  app.get('/prices/batch', async (request, reply) => {
    const { ids } = request.query as { ids?: string }
    if (!ids) return reply.status(400).send({ error: 'ids query param required' })

    const idList = ids.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 50)
    if (idList.length === 0) return {}

    const [rows, references] = await Promise.all([
      prisma.skinPrice.findMany({ where: { skinId: { in: idList } } }),
      fetchReferencePrices(idList),
    ])

    const response: Record<string, object> = {}
    for (const row of rows) {
      response[row.skinId] = {
        skinportPrice:   row.skinportPrice,
        csgoMarketPrice: row.csgoMarketPrice,
        waxpeerPrice:    row.waxpeerPrice,
        csfloatPrice:    row.csfloatPrice,
        lowestPrice:     row.lowestPrice,
        // Same fallback as GET /skins: the bulk job's cached value when there is
        // no reference point in the window.
        priceChange24h:  change24h(row, references.get(row.skinId)) ?? row.priceChange24h,
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
}
