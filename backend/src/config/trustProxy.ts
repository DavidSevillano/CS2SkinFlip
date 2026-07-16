/**
 * Fastify hands `trustProxy` straight to `proxy-addr`, which accepts a boolean,
 * a hop count, or a comma-separated IP/CIDR allowlist. Env vars are always
 * strings, so coerce to the type proxy-addr expects.
 *
 * Prefer a hop count over `true`. `true` trusts the entire X-Forwarded-For
 * chain and resolves `request.ip` to its *leftmost* entry — which is whatever
 * the client sent, and is therefore forgeable. An attacker could rotate the
 * header to mint a fresh rate-limit bucket on every request. A hop count
 * resolves to an address appended by our own proxies instead, which a client
 * cannot influence.
 */
export function parseTrustProxy(raw: string | undefined): boolean | number | string {
  const value = (raw ?? '').trim()
  if (value === '' || value === 'false') return false
  if (value === 'true') return true
  if (/^\d+$/.test(value)) return Number(value)
  return value
}
