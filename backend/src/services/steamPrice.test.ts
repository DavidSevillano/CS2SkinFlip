import { describe, it, expect, vi, beforeEach } from 'vitest'

const { redisGet, redisSet } = vi.hoisted(() => ({ redisGet: vi.fn(), redisSet: vi.fn() }))
vi.mock('../redis/client', () => ({
  redis: { get: redisGet, set: redisSet },
  CACHE_TTL: { STEAM_PRICE: 3600 },
}))

const { axiosGet } = vi.hoisted(() => ({ axiosGet: vi.fn() }))
vi.mock('axios', () => ({ default: { get: axiosGet } }))

const { getSteamPrice } = await import('./steamPrice')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

describe('getSteamPrice', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    redisGet.mockResolvedValue(null)
  })

  it('returns the cached price without calling Steam', async () => {
    redisGet.mockResolvedValue({ price: 12.34 })

    const price = await getSteamPrice('skin-1', 'AK-47 | Redline (Field-Tested)', log)

    expect(price).toBe(12.34)
    expect(axiosGet).not.toHaveBeenCalled()
  })

  it('returns a cached null (previously unlisted) without calling Steam', async () => {
    redisGet.mockResolvedValue({ price: null })

    const price = await getSteamPrice('skin-1', 'AK-47 | Redline (Field-Tested)', log)

    expect(price).toBeNull()
    expect(axiosGet).not.toHaveBeenCalled()
  })

  it('parses lowest_price on a cache miss and caches the result', async () => {
    axiosGet.mockResolvedValue({ data: { success: true, lowest_price: '$32.39', volume: '10' } })

    const price = await getSteamPrice('skin-1', 'AK-47 | Redline (Field-Tested)', log)

    expect(price).toBe(32.39)
    expect(axiosGet).toHaveBeenCalledWith(
      'https://steamcommunity.com/market/priceoverview/',
      expect.objectContaining({
        params: { appid: 730, currency: 1, market_hash_name: 'AK-47 | Redline (Field-Tested)' },
      }),
    )
    expect(redisSet).toHaveBeenCalledWith('steam-price:skin-1', { price: 32.39 }, { ex: 3600 })
  })

  it('parses a price with a thousands separator', async () => {
    axiosGet.mockResolvedValue({ data: { success: true, lowest_price: '$1,234.56' } })

    const price = await getSteamPrice('skin-1', '★ Karambit | Doppler (Factory New)', log)

    expect(price).toBe(1234.56)
  })

  it('caches null when Steam reports the item as not listed (success: false)', async () => {
    axiosGet.mockResolvedValue({ data: { success: false } })

    const price = await getSteamPrice('skin-1', 'Some Unlisted Skin', log)

    expect(price).toBeNull()
    expect(redisSet).toHaveBeenCalledWith('steam-price:skin-1', { price: null }, { ex: 3600 })
  })

  it('caches null instead of throwing when the request fails (timeout, rate-limit, etc.)', async () => {
    axiosGet.mockRejectedValue(new Error('timeout of 8000ms exceeded'))

    const price = await getSteamPrice('skin-1', 'AK-47 | Redline (Field-Tested)', log)

    expect(price).toBeNull()
    expect(redisSet).toHaveBeenCalledWith('steam-price:skin-1', { price: null }, { ex: 3600 })
    expect(log.warn).toHaveBeenCalled()
  })
})
