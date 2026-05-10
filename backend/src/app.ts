import 'dotenv/config'
import { buildServer } from './server'
import { env } from './config/env'
import { prisma } from './db/prisma'
import { startPriceRefreshJob } from './jobs/priceRefresh'
import { populateSkins } from './jobs/populateSkins'
import { populatePrices } from './jobs/populatePrices'
import { redis } from './redis/client'

async function main() {
  const app = await buildServer()

  const graceful = async (signal: string) => {
    app.log.info(`Received ${signal}, shutting down...`)
    await app.close()
    await prisma.$disconnect()
    process.exit(0)
  }

  process.on('SIGTERM', () => graceful('SIGTERM'))
  process.on('SIGINT', () => graceful('SIGINT'))

  try {
    await app.listen({ port: env.PORT, host: env.HOST })
    // Clear ALL price caches on startup so clients get fresh data immediately
    const keys = await redis.keys('prices:*')
    if (keys.length > 0) await Promise.all(keys.map(k => redis.del(k)))
    await redis.del('top-movers:20')

    // Chain: populate skins → populate prices → start refresh job
    populateSkins(app.log)
      .then(() => populatePrices(app.log))
      .then(() => startPriceRefreshJob(app.log))
      .catch((err) => app.log.error('[Startup] Job chain failed:', err))
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}

main()
