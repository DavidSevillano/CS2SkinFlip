import { FastifyInstance } from 'fastify'
import { authRoutes } from './auth'
import { skinRoutes } from './skins'
import { portfolioRoutes } from './portfolio'
import { watchlistRoutes } from './watchlist'
import { alertRoutes } from './alerts'
import { priceRoutes } from './prices'
import { debugRoutes } from './debug'
import { jobsRoutes } from './jobs'
import { billingRoutes } from './billing'

export async function registerRoutes(app: FastifyInstance) {
  await app.register(authRoutes)
  await app.register(skinRoutes)
  await app.register(portfolioRoutes)
  await app.register(watchlistRoutes)
  await app.register(alertRoutes)
  await app.register(priceRoutes)
  await app.register(debugRoutes)
  await app.register(jobsRoutes)
  await app.register(billingRoutes)
}
