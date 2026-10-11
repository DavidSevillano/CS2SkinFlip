import { createHash } from 'crypto'

// Lifetime of a JWT issued by the backend. Short enough that a leaked token
// stops working on its own; long enough that the sliding renewal below means an
// active user never sees a sign-in prompt.
export const TOKEN_TTL = '30d'

// An authenticated request whose token expires within this window gets a fresh
// one back in REFRESHED_TOKEN_HEADER. Also covers legacy tokens issued before
// TOKEN_TTL existed, which carry no `exp` at all.
export const TOKEN_REFRESH_WITHIN_SECONDS = 7 * 24 * 60 * 60

export const REFRESHED_TOKEN_HEADER = 'x-refreshed-token'

// One-time login codes handed to the app instead of a JWT (PKCE, RFC 7636).
// Two minutes covers the app exchanging it straight after the redirect.
export const AUTH_CODE_TTL_SECONDS = 120
export const AUTH_CODE_KEY_PREFIX = 'auth:code:'

// S256 challenge: base64url(sha256(verifier)), 43 chars without padding.
export const CHALLENGE_PATTERN = /^[A-Za-z0-9_-]{43}$/
// RFC 7636 §4.1: 43–128 chars from the unreserved set.
export const VERIFIER_PATTERN = /^[A-Za-z0-9._~-]{43,128}$/

export function challengeFor(verifier: string): string {
  return createHash('sha256').update(verifier).digest('base64url')
}
