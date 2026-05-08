import 'dotenv/config'
import { buildServer } from './server'
import { env } from './config/env'
import { prisma } from './db/prisma'
import { startPriceRefreshJob } from './jobs/priceRefresh'
import { populateSkins } from './jobs/populateSkins'
import { populatePricesFromSteam } from './jobs/populatePrices'
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
    // Clear stale top-movers cache on every startup
    await redis.del('top-movers:20')
    // Chain: populate skins → populate prices → start refresh job
    populateSkins(app.log)
      .then(() => populatePricesFromSteam(app.log, 5))
      .then(() => startPriceRefreshJob(app.log))
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}

main()
