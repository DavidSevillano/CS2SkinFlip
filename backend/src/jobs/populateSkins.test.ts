import { describe, it, expect, vi, beforeEach } from 'vitest'

const { createMany } = vi.hoisted(() => ({ createMany: vi.fn() }))
vi.mock('../db/prisma', () => ({ prisma: { skin: { createMany } } }))

const { axiosGet } = vi.hoisted(() => ({ axiosGet: vi.fn() }))
vi.mock('axios', () => ({ default: { get: axiosGet } }))

const { redis } = await vi.hoisted(async () => {
  const { fakeRedisStore } = await import('../test/fakeRedis')
  return { redis: fakeRedisStore() }
})
vi.mock('../redis/client', () => ({ redis }))

const { invalidateTopMoversCache } = vi.hoisted(() => ({ invalidateTopMoversCache: vi.fn() }))
vi.mock('../services/prices', () => ({ invalidateTopMoversCache }))

const { catalogRecords, syncSkinCatalog, syncSkinCatalogIfDue, CATALOG_SYNCED_AT_KEY } = await import('./populateSkins')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

function catalogSkin(over: Record<string, unknown> = {}) {
  return {
    id: 'skin-1',
    name: 'AK-47 | Redline',
    weapon: { id: 'w', name: 'AK-47' },
    rarity: { id: 'r', name: 'Classified', color: '#d32ce6' },
    stattrak: true,
    souvenir: false,
    wears: [{ id: 'ft', name: 'Field-Tested' }],
    image: 'https://img/ak.png',
    ...over,
  }
}

describe('catalogRecords', () => {
  it('names StatTrak variants the way the market does, knives included', () => {
    const names = catalogRecords([
      catalogSkin(),
      catalogSkin({ name: '★ Karambit | Fade', wears: [{ id: 'fn', name: 'Factory New' }] }),
    ] as any).map((r) => r.marketHashName)

    expect(names).toEqual([
      'AK-47 | Redline (Field-Tested)',
      'StatTrak™ AK-47 | Redline (Field-Tested)',
      '★ Karambit | Fade (Factory New)',
      '★ StatTrak™ Karambit | Fade (Factory New)',
    ])
  })
})

describe('catalogRecords — souvenirs', () => {
  const withSouvenir = catalogSkin({ stattrak: false, souvenir: true, wears: [{ id: 'fn', name: 'Factory New' }, { id: 'ft', name: 'Field-Tested' }] })

  // The catalog says a souvenir version exists, not in which wears.
  it('adds a souvenir only in the wears a marketplace quotes', () => {
    const names = catalogRecords([withSouvenir] as any, (n) => n === 'Souvenir AK-47 | Redline (Field-Tested)')
      .map((r) => r.marketHashName)

    expect(names).toEqual([
      'AK-47 | Redline (Factory New)',
      'AK-47 | Redline (Field-Tested)',
      'Souvenir AK-47 | Redline (Field-Tested)',
    ])
  })

  it('adds no souvenirs when nothing says which are listed', () => {
    expect(catalogRecords([withSouvenir] as any).map((r) => r.marketHashName)).toEqual([
      'AK-47 | Redline (Factory New)',
      'AK-47 | Redline (Field-Tested)',
    ])
  })
})

describe('syncSkinCatalog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    redis.store.clear()
    axiosGet.mockResolvedValue({ data: [catalogSkin()] })
  })

  // Re-sending the whole catalog daily must not rewrite or read back the
  // 24k rows already there.
  it('inserts only what is missing, without reading existing rows back', async () => {
    createMany.mockResolvedValue({ count: 1 })

    expect(await syncSkinCatalog(log)).toEqual({ inserted: 1 })
    expect(createMany).toHaveBeenCalledWith(expect.objectContaining({ skipDuplicates: true }))
    expect(invalidateTopMoversCache).toHaveBeenCalled()
  })

  it('leaves the top-movers cache alone when nothing was new', async () => {
    createMany.mockResolvedValue({ count: 0 })

    await syncSkinCatalog(log)

    expect(invalidateTopMoversCache).not.toHaveBeenCalled()
  })
})

describe('syncSkinCatalogIfDue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    redis.store.clear()
    axiosGet.mockResolvedValue({ data: [catalogSkin()] })
    createMany.mockResolvedValue({ count: 0 })
  })

  it('syncs when it has never run, then waits a day', async () => {
    expect(await syncSkinCatalogIfDue(log)).toEqual({ inserted: 0 })
    expect(await syncSkinCatalogIfDue(log)).toBeNull()
    expect(axiosGet).toHaveBeenCalledTimes(1)
  })

  it('syncs again once a day has passed', async () => {
    await redis.set(CATALOG_SYNCED_AT_KEY, Date.now() - 25 * 60 * 60 * 1000)

    expect(await syncSkinCatalogIfDue(log)).not.toBeNull()
  })

  // A failed download must be retried on the next 6h run, not a day later.
  it('does not record a sync that failed', async () => {
    axiosGet.mockRejectedValue(new Error('GitHub down'))

    await expect(syncSkinCatalogIfDue(log)).rejects.toThrow()
    expect(redis.store.has(CATALOG_SYNCED_AT_KEY)).toBe(false)
  })
})
