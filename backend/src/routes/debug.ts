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
   * GET /debug/client-ip
   * Reports how `request.ip` is currently being resolved, so TRUST_PROXY can be
   * set from real data instead of a guess about the host's proxy layers.
   *
   * Call it from a machine whose public IP you know, then set TRUST_PROXY to the
   * `hops` value in `candidatesByHopCount` whose `ip` matches that address.
   * `resolvedIp` should equal it afterwards.
   */
  app.get('/debug/client-ip', async (request) => {
    const rawXff = request.headers['x-forwarded-for']
    const chain = (Array.isArray(rawXff) ? rawXff.join(',') : rawXff ?? '')
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean)

    // proxy-addr sees [socket, ...chain reversed] and, for a hop count of N,
    // trusts the N addresses nearest us and resolves to the next one out —
    // i.e. index N. Rebuild that list by hand rather than reading request.ips,
    // which is itself derived from the TRUST_PROXY value under test.
    const addrs = [request.socket.remoteAddress ?? '', ...[...chain].reverse()]

    return {
      configuredTrustProxy: env.TRUST_PROXY,
      resolvedIp: request.ip,
      socketRemoteAddress: request.socket.remoteAddress,
      xForwardedFor: chain,
      candidatesByHopCount: addrs
        .map((ip, hops) => ({ hops, ip }))
        .filter(({ hops }) => hops > 0),
      // Render fronts us with Cloudflare. If it forwards CF-Connecting-IP, that
      // is a single overwritten (non-chained) value we could key on directly
      // instead of counting hops. Reported so one call settles which approach
      // applies; unset means the hop count is the only option.
      cloudflareHeaders: {
        'cf-connecting-ip': request.headers['cf-connecting-ip'] ?? null,
        'true-client-ip': request.headers['true-client-ip'] ?? null,
        'cf-ray': request.headers['cf-ray'] ?? null,
      },
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
