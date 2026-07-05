import { FastifyRequest, FastifyReply } from 'fastify'

export async function authenticate(request: FastifyRequest, reply: FastifyReply) {
  try {
    await request.jwtVerify()
    // Single-use tokens (password reset / email verification) must never work as session auth
    if (request.user.purpose) {
      return reply.status(401).send({ error: 'Unauthorized', message: 'Valid JWT required' })
    }
  } catch {
    reply.status(401).send({ error: 'Unauthorized', message: 'Valid JWT required' })
  }
}
