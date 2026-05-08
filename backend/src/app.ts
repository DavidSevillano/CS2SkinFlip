import 'dotenv/config'
import { buildServer } from './server'
import { env } from './config/env'
import { prisma } from './db/prisma'
import { startPriceRefreshJob } from './jobs/priceRefresh'
import { populateSkins } from './jobs/populateSkins'

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
    // First populate DB with real skins (only runs if DB has < 50 skins)
    populateSkins(app.log).then(() => startPriceRefreshJob(app.log))
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}

main()
