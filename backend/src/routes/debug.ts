import { FastifyPluginAsync } from 'fastify'
import { AlertService } from '../services/alerts'
import { env } from '../config/env'

const alertService = new AlertService()

/**
 * Debug routes — only active when DEBUG_SECRET is set in .env.
 * Protect every call with the header:  X-Debug-Secret: <value>
 */
export const debugRoutes: FastifyPluginAsync = async (app) => {
  if (!env.DEBUG_SECRET) {
    app.log.info('[Debug] DEBUG_SECRET not set — debug routes disabled')
    return
  }

  // Guard: reject requests that don't carry the secret
  app.addHook('onRequest', async (request, reply) => {
    if (request.headers['x-debug-secret'] !== env.DEBUG_SECRET) {
      reply.status(401).send({ error: 'Unauthorized' })
    }
  })

  /**
   * POST /debug/check-alerts
   * Runs AlertService.checkAll() immediately and returns a summary of
   * which alerts were triggered during this run.
   */
  app.post('/debug/check-alerts', async () => {
    const before = Date.now()
    const triggered = await alertService.checkAllWithResult()
    return {
      ok: true,
      durationMs: Date.now() - before,
      triggeredCount: triggered.length,
      triggered,
    }
  })
}
