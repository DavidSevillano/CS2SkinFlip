import { prisma } from '../db/prisma'
import type { FastifyBaseLogger } from 'fastify'

const RETENTION_DAYS = 90

export async function cleanupPriceHistory(log: FastifyBaseLogger) {
  const cutoff = new Date(Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000)
  const { count } = await prisma.priceHistory.deleteMany({
    where: { timestamp: { lt: cutoff } },
  })
  log.info(`[PriceHistoryCleanup] Deleted ${count} rows older than ${RETENTION_DAYS} days`)
}
