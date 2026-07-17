/**
 * A Map-backed stand-in for the Upstash client, with just enough glob support to
 * answer `keys`.
 *
 * Exists because call-recording mocks cannot see cache-invalidation bugs: an
 * assertion on `del('top-movers:20')` passed for as long as the key was wrong,
 * because nothing checked that the key was one the writer had ever written.
 * A fake that really stores what the writer stores can tell the difference.
 */
export function fakeRedisStore() {
  const store = new Map<string, unknown>()

  const toRegExp = (pattern: string) =>
    new RegExp(
      `^${pattern
        .split('*')
        .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
        .join('.*')}$`,
    )

  return {
    store,
    get: async (key: string) => store.get(key) ?? null,
    set: async (key: string, value: unknown) => {
      store.set(key, value)
      return 'OK'
    },
    keys: async (pattern: string) => [...store.keys()].filter((key) => toRegExp(pattern).test(key)),
    del: async (key: string) => (store.delete(key) ? 1 : 0),
  }
}
