import { populatePrices } from './populatePrices'
import { cleanupPriceHistory } from './cleanupPriceHistory'
import { AlertService } from '../services/alerts'
import { REFRESH_INTERVAL_MS } from '../config/priceHistory'
import type { FastifyBaseLogger } from 'fastify'

const alertService = new AlertService()

export function startPriceRefreshJob(log: FastifyBaseLogger) {
  const run = async () => {
    await populatePrices(log)
    log.info('[AlertCheck] Checking active alerts after price refresh…')
    await alertService.checkAll()
    log.info('[AlertCheck] Done')
    await cleanupPriceHistory(log)
  }

  const interval = setInterval(() => {
    run().catch((err) => log.error({ err }, '[PriceRefresh] Scheduled run failed'))
  }, REFRESH_INTERVAL_MS)

  log.info(`[PriceRefresh] Job started — refreshing every ${REFRESH_INTERVAL_MS / 1000 / 60} minutes`)

  return interval
}
