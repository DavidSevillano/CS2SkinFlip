import { FastifyRequest, FastifyReply } from 'fastify'
import { prisma } from '../db/prisma'
import { REFRESHED_TOKEN_HEADER, TOKEN_REFRESH_WITHIN_SECONDS, TOKEN_TTL } from '../config/auth'

export async function authenticate(request: FastifyRequest, reply: FastifyReply) {
  try {
    await request.jwtVerify()
  } catch {
    return reply.status(401).send({ error: 'Unauthorized', message: 'Valid JWT required' })
  }

  const { userId, steamId } = request.user
  const user = await prisma.user.findUnique({ where: { id: userId }, select: { id: true } })
  if (!user) {
    return reply.status(401).send({ error: 'Unauthorized', message: 'User no longer exists' })
  }

  // Sliding renewal: a token close to expiry — or a legacy one with no expiry
  // at all — gets a fresh one alongside the response, which current app
  // versions store. Older versions ignore the header.
  const { exp } = request.user as { exp?: number }
  const secondsLeft = exp === undefined ? 0 : exp - Math.floor(Date.now() / 1000)
  if (secondsLeft < TOKEN_REFRESH_WITHIN_SECONDS) {
    const fresh = await reply.jwtSign({ userId, steamId }, { expiresIn: TOKEN_TTL })
    reply.header(REFRESHED_TOKEN_HEADER, fresh)
  }
}
