import { FastifyPluginAsync } from 'fastify'
import { prisma } from '../db/prisma'
import { redis, CACHE_TTL } from '../redis/client'
import { PriceService } from '../services/prices'
import { fetchCS2CapLivePrice, fetchCSFloatLivePrice } from '../jobs/populatePrices'

const priceService = new PriceService()

export const priceRoutes: FastifyPluginAsync = async (app) => {
  /**
   * GET /prices/batch?ids=id1,id2,...
   *
   * Returns accurate prices for up to 50 skins in a single round-trip.
   *
   * - CS2Cap:     DB value from bulk job (2h). If null, calls live CS2Cap API
   *               (concurrency capped at 5) to get the aggregated lowest ask.
   * - CSFloat:    Redis `csfloat-live:{id}` cache (5-min TTL) first; if not
   *               cached, calls live CSFloat API (concurrency capped at 5);
   *               falls back to DB CSFloat price.
   * - CS:GO Market: DB value (reliable, refreshed by bulk job every 2h).
   *
   * Android calls this once after each list load so cards display correct prices on first render.
   */
  app.get('/prices/batch', async (request, reply) => {
    const { ids } = request.query as { ids?: string }
    if (!ids) return reply.status(400).send({ error: 'ids query param required' })

    const idList = ids.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 50)
    if (idList.length === 0) return {}

    const skins = await prisma.skin.findMany({
      where: { id: { in: idList } },
      include: { price: true },
    })

    // ── Phase 1: gather DB prices + csfloat-live cache in parallel ───────────
    type SkinPhase1 = {
      skin: typeof skins[number]
      dbCS2Cap: number | null
      dbCsfloat: number | null
      csgoMarketPrice: number | null
      csfloatLiveCached: number | null
    }

    const phase1 = await Promise.allSettled(
      skins.map(async (skin): Promise<SkinPhase1> => {
        const csfloatLiveCached = await redis.get<number>(`csfloat-live:${skin.id}`).catch(() => null)
        return {
          skin,
          dbCS2Cap: skin.price?.cs2capPrice ?? null,
          dbCsfloat: skin.price?.csfloatPrice ?? null,
          csgoMarketPrice: skin.price?.csgoMarketPrice ?? null,
          csfloatLiveCached,
        }
      })
    )

    const phase1Data: SkinPhase1[] = phase1
      .filter(r => r.status === 'fulfilled')
      .map(r => (r as PromiseFulfilledResult<SkinPhase1>).value)

    const CONCURRENCY = 5

    // ── Phase 2a: live CS2Cap for skins with null DB price ───────────────────
    const needsCS2CapLive = phase1Data.filter(d => d.dbCS2Cap === null)
    const liveCS2CapMap = new Map<string, number>()

    for (let i = 0; i < needsCS2CapLive.length; i += CONCURRENCY) {
      const batch = needsCS2CapLive.slice(i, i + CONCURRENCY)
      const results = await Promise.allSettled(
        batch.map(({ skin }) =>
          fetchCS2CapLivePrice(skin.marketHashName).then(price => ({ id: skin.id, price }))
        )
      )
      for (const r of results) {
        if (r.status === 'fulfilled' && r.value.price !== null) {
          liveCS2CapMap.set(r.value.id, r.value.price)
        }
      }
    }

    // ── Phase 2b: live CSFloat for skins with no csfloat-live cache AND no DB value ──
    // Skins that already have a DB csfloatPrice (written by the detail endpoint or a
    // previous batch call) skip the live call — the DB value is recent enough.
    // Live calls only happen when we have absolutely no data for a skin.
    const needsCSFloatLive = phase1Data.filter(d => d.csfloatLiveCached === null && d.dbCsfloat === null)
    const liveCSFloatMap = new Map<string, number>()

    for (let i = 0; i < needsCSFloatLive.length; i += CONCURRENCY) {
      const batch = needsCSFloatLive.slice(i, i + CONCURRENCY)
      const results = await Promise.allSettled(
        batch.map(({ skin }) =>
          fetchCSFloatLivePrice(skin.marketHashName).then(price => ({ id: skin.id, price }))
        )
      )
      for (const r of results) {
        if (r.status === 'fulfilled' && r.value.price !== null) {
          liveCSFloatMap.set(r.value.id, r.value.price)
          redis.set(`csfloat-live:${r.value.id}`, r.value.price, { ex: CACHE_TTL.SKIN_PRICES }).catch(() => {})
        }
      }
    }

    // ── Phase 3: assemble response ────────────────────────────────────────────
    const response: Record<string, object> = {}
    for (const { skin, dbCS2Cap, dbCsfloat, csgoMarketPrice, csfloatLiveCached } of phase1Data) {
      const cs2capPrice  = liveCS2CapMap.get(skin.id) ?? dbCS2Cap
      const csfloatPrice = liveCSFloatMap.get(skin.id)
        ?? (csfloatLiveCached !== null ? csfloatLiveCached : null)
        ?? dbCsfloat

      const positives = [cs2capPrice, csfloatPrice, csgoMarketPrice].filter(
        (p): p is number => p != null && p > 0,
      )
      const lowestPrice = positives.length > 0 ? Math.min(...positives) : null
      response[skin.id] = { cs2capPrice, csfloatPrice, csgoMarketPrice, lowestPrice }
    }

    return response
  })

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

    const prices = await priceService.getPricesForSkin(skinId, skin.marketHashName)
    return prices
  })
}
