import { FastifyRequest, FastifyReply } from 'fastify'
import { prisma } from '../db/prisma'

export async function authenticate(request: FastifyRequest, reply: FastifyReply) {
  try {
    await request.jwtVerify()
  } catch {
    return reply.status(401).send({ error: 'Unauthorized', message: 'Valid JWT required' })
  }

  const { userId } = request.user
  const user = await prisma.user.findUnique({ where: { id: userId }, select: { id: true } })
  if (!user) {
    return reply.status(401).send({ error: 'Unauthorized', message: 'User no longer exists' })
  }
}
