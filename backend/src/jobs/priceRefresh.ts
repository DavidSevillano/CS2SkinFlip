import { populatePrices } from './populatePrices'
import { AlertService } from '../services/alerts'
import type { FastifyBaseLogger } from 'fastify'

const REFRESH_INTERVAL_MS = 2 * 60 * 60 * 1000 // 2 hours
const alertService = new AlertService()

export function startPriceRefreshJob(log: FastifyBaseLogger) {
  const run = async () => {
    await populatePrices(log)
    log.info('[AlertCheck] Checking active alerts after price refresh…')
    await alertService.checkAll()
    log.info('[AlertCheck] Done')
  }

  const interval = setInterval(() => {
    run().catch((err) => log.error('[PriceRefresh] Scheduled run failed:', err))
  }, REFRESH_INTERVAL_MS)

  log.info(`[PriceRefresh] Job started — refreshing every ${REFRESH_INTERVAL_MS / 1000 / 60} minutes`)

  return interval
}
