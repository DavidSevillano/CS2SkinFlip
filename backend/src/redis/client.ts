import { Redis } from '@upstash/redis'
import { env } from '../config/env'

export const redis = new Redis({
  url: env.UPSTASH_REDIS_REST_URL,
  token: env.UPSTASH_REDIS_REST_TOKEN,
})

export const CACHE_TTL = {
  SKIN_PRICES: 60 * 5,         // 5 min
  TOP_MOVERS: 60 * 15,         // 15 min
  INVENTORY: 60 * 10,          // 10 min
  PLAYER_SUMMARY: 60 * 60,     // 1 hour
} as const

/** Sliding-window-ish rate limit: allows `limit` calls per `windowSeconds` per key. */
export async function checkRateLimit(key: string, limit: number, windowSeconds: number): Promise<boolean> {
  const count = await redis.incr(`ratelimit:${key}`)
  if (count === 1) await redis.expire(`ratelimit:${key}`, windowSeconds)
  return count <= limit
}
