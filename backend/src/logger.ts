import pino from 'pino'
import type { FastifyBaseLogger } from 'fastify'
import { env } from './config/env'

/**
 * The process's one pino instance. Fastify logs through it (`server.ts`), and
 * so does code that runs without a request or a Fastify instance at hand — the
 * FCM and Google Play clients — instead of `console`, which ignored LOG_LEVEL
 * and broke the JSON stream Render ingests.
 */
// Typed as Fastify's logger so routes keep their usual FastifyInstance type.
export const logger: FastifyBaseLogger = pino({
  level: env.LOG_LEVEL,
  transport:
    env.NODE_ENV === 'development'
      ? { target: 'pino-pretty', options: { colorize: true } }
      : undefined,
})
