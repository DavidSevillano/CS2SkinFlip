import { createHash, timingSafeEqual } from 'crypto'
import type { FastifyReply, FastifyRequest } from 'fastify'

/**
 * `onRequest` hook that rejects any request whose `headerName` doesn't carry
 * `secret`. For machine-to-machine routes (the price cron, the site generator,
 * debug tooling) that have no user to authenticate.
 *
 * Compared in constant time: `!==` returns at the first differing character,
 * which leaks how much of a guess was right. Both sides are hashed first so the
 * comparison never depends on the secret's length either — `timingSafeEqual`
 * throws on buffers of different sizes.
 */
export function requireSecretHeader(headerName: string, secret: string) {
  const expected = createHash('sha256').update(secret).digest()

  return async (request: FastifyRequest, reply: FastifyReply) => {
    const raw = request.headers[headerName.toLowerCase()]
    const provided = typeof raw === 'string' ? raw : ''
    const actual = createHash('sha256').update(provided).digest()
    if (!timingSafeEqual(actual, expected)) {
      // `return reply` stops the lifecycle explicitly rather than relying on
      // Fastify noticing the reply was already sent.
      return reply.status(401).send({ error: 'Unauthorized' })
    }
  }
}
