import { FastifyPluginAsync } from 'fastify'
import { env } from '../config/env'
import { runRefreshPrices, getRefreshStatus } from '../jobs/runner'

/**
 * Job routes — the price pipeline's external trigger (a GitHub Actions cron).
 * Only active when JOBS_SECRET is set in .env.
 * Protect every call with the header:  X-Jobs-Secret: <value>
 */
export const jobsRoutes: FastifyPluginAsync = async (app) => {
  if (!env.JOBS_SECRET) {
    app.log.warn('[Jobs] JOBS_SECRET not set — job routes disabled; the scheduled price refresh cannot run')
    return
  }

  // Guard: reject requests that don't carry the secret
  app.addHook('onRequest', async (request, reply) => {
    if (request.headers['x-jobs-secret'] !== env.JOBS_SECRET) {
      reply.status(401).send({ error: 'Unauthorized' })
    }
  })

  /**
   * POST /jobs/refresh-prices
   * Starts a full refresh and answers 202 immediately — the job takes minutes,
   * far too long to hold an HTTP connection open through Render's proxy. The
   * caller polls the status endpoint below.
   */
  app.post('/jobs/refresh-prices', async (_request, reply) => {
    const result = await runRefreshPrices(app.log)
    if (!result.started) {
      return reply.status(409).send({ error: result.reason })
    }
    return reply.status(202).send({ ok: true, runId: result.runId })
  })

  /** GET /jobs/refresh-prices/status — the last run's state. */
  app.get('/jobs/refresh-prices/status', async (_request, reply) => {
    const status = await getRefreshStatus()
    if (!status) {
      return reply.status(404).send({ error: 'no-run-recorded' })
    }
    return status
  })
}
