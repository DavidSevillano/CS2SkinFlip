import { z } from 'zod'
import { parseTrustProxy } from './trustProxy'

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  LOG_LEVEL: z
    .enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent'])
    .default('info'),
  PORT: z.coerce.number().default(3000),
  HOST: z.string().default('0.0.0.0'),
  DATABASE_URL: z.string({ required_error: 'DATABASE_URL is required' }),
  UPSTASH_REDIS_REST_URL: z.string({ required_error: 'UPSTASH_REDIS_REST_URL is required' }),
  UPSTASH_REDIS_REST_TOKEN: z.string({ required_error: 'UPSTASH_REDIS_REST_TOKEN is required' }),
  JWT_SECRET: z.string().min(32, 'JWT_SECRET must be at least 32 characters'),
  STEAM_API_KEY: z.string({ required_error: 'STEAM_API_KEY is required' }),
  STEAM_CALLBACK_URL: z.string().default('http://localhost:3000/auth/steam/callback'),
  FRONTEND_URL: z.string().default('http://localhost:8080'),
  MOBILE_DEEP_LINK: z.string().default('cs2skinflip://auth/callback'),
  DEBUG_SECRET: z.string().optional(),
  FCM_SERVICE_ACCOUNT_PATH: z.string().optional(),
  GOOGLE_PLAY_SERVICE_ACCOUNT_PATH: z.string().optional(),
  GOOGLE_PLAY_PACKAGE_NAME: z.string().default('com.burixer85.cs2skinflip'),
  PREMIUM_PRODUCT_ID: z.string().default('premium_unlimited_alerts'),
  // Number of reverse-proxy hops in front of the app, so `request.ip` (and the
  // per-IP rate limit keyed on it) resolves to the caller rather than the proxy.
  // `false` locally where nothing fronts us; on a host like Render, the hop
  // count — measure it with GET /debug/client-ip. Avoid `true`: it trusts the
  // whole X-Forwarded-For chain, making the resolved IP client-forgeable.
  TRUST_PROXY: z.string().default('false').transform(parseTrustProxy),
  // Global per-IP rate limit (requests per window). Search routes tighten this.
  RATE_LIMIT_MAX: z.coerce.number().int().positive().default(100),
  RATE_LIMIT_WINDOW: z.string().default('1 minute'),
  RATE_LIMIT_SEARCH_MAX: z.coerce.number().int().positive().default(30),
})

export type Env = z.infer<typeof envSchema>

const parsed = envSchema.safeParse(process.env)

if (!parsed.success) {
  console.error('❌ Invalid environment variables:')
  console.error(parsed.error.flatten().fieldErrors)
  process.exit(1)
}

export const env = parsed.data
