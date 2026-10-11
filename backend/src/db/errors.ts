import { Prisma } from '@prisma/client'

/**
 * A write referenced a row that doesn't exist (Prisma P2003). With the foreign
 * keys to `Skin`, that is what an unknown `skinId` in a request body becomes —
 * a 404 for the client, not a 500.
 */
export function isForeignKeyViolation(e: unknown): boolean {
  return e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2003'
}
