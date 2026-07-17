import 'dotenv/config'
import { buildServer } from './server'
import { env } from './config/env'
import { prisma } from './db/prisma'
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

    // The periodic refresh is triggered externally (a GitHub Actions cron ->
    // POST /jobs/refresh-prices), not scheduled here: a setInterval in this
    // process restarts its clock on every deploy and would double up if the
    // service ever ran 2 instances. populatePrices only runs on a real
    // bootstrap, so a routine restart no longer fires a full bulk fetch.
    populateSkins(app.log)
      .then(async () => {
        const priced = await prisma.skinPrice.count()
        if (priced === 0) {
          app.log.info('[Startup] SkinPrice empty — seeding prices once')
          await populatePrices(app.log)
          return
        }
        app.log.info(`[Startup] SkinPrice already has ${priced} rows — refresh left to the cron`)
      })
      .catch((err) => app.log.error({ err }, '[Startup] Job chain failed'))
  } catch (err) {
    app.log.error({ err }, '[Startup] Server failed to start')
    process.exit(1)
  }
}

main()
