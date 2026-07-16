import Fastify from 'fastify'
import cors from '@fastify/cors'
import cookie from '@fastify/cookie'
import jwt from '@fastify/jwt'
import rateLimit from '@fastify/rate-limit'
import { env } from './config/env'
import { registerRoutes } from './routes'
import { getPriceHealth } from './jobs/populatePrices'

export async function buildServer() {
  const app = Fastify({
    // Render fronts us with Cloudflare + its own load balancer, so the socket
    // address is theirs, not the caller's. Without this every user shares one
    // rate-limit bucket. See TRUST_PROXY in config/env.ts for why this is a hop
    // count and not `true`.
    trustProxy: env.TRUST_PROXY,
    logger: {
      level: env.LOG_LEVEL,
      transport:
        env.NODE_ENV === 'development'
          ? { target: 'pino-pretty', options: { colorize: true } }
          : undefined,
    },
  })

  await app.register(cors, {
    origin: [env.FRONTEND_URL],
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  })

  // Global per-IP rate limit. Protects Neon/Redis from abuse across every
  // route; the DB-heavy public search (/skins) tightens this further per-route.
  await app.register(rateLimit, {
    global: true,
    max: env.RATE_LIMIT_MAX,
    timeWindow: env.RATE_LIMIT_WINDOW,
  })

  await app.register(cookie)

  await app.register(jwt, {
    secret: env.JWT_SECRET,
    cookie: { cookieName: 'token', signed: false },
  })

  // `prices` is what the uptime monitor keys on: a process that answers but
  // serves prices nothing has refreshed in 8h is not healthy. Only 'fresh'
  // contains the keyword, so 'stale'/'unknown' both alert.
  app.get('/health', async () => {
    const prices = await getPriceHealth()
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
      env: env.NODE_ENV,
      prices: prices.freshness,
      pricesLastRun: prices.lastRun?.toISOString() ?? null,
    }
  })

  await registerRoutes(app)

  return app
}
