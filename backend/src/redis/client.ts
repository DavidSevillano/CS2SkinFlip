import { Redis } from '@upstash/redis'
import { env } from '../config/env'

export const redis = new Redis({
  url: env.UPSTASH_REDIS_REST_URL,
  token: env.UPSTASH_REDIS_REST_TOKEN,
})

export const CACHE_TTL = {
  SKIN_PRICES: 60 * 5,         // 5 min
  TOP_MOVERS: 60 * 15,         // 15 min
  PLAYER_SUMMARY: 60 * 60,     // 1 hour
  STEAM_PRICE: 60 * 60,        // 1 hour — Steam has no bulk endpoint and blocks
                                // aggressively on rate-limit, so this is longer
                                // than the other marketplace caches.
} as const
