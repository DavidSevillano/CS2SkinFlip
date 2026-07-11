import { describe, it, expect, vi, beforeEach } from 'vitest'

// Regression guard for the Render OOM (2026-07): populatePrices used to load the
// 24h-change reference with an unbounded `where: { timestamp: { lte: dayAgo } }`,
// which matches the entire retained history (~35 days, millions of rows) and,
// because `distinct` can't be pushed to Postgres under a timestamp `orderBy`,
// Prisma materialised every row in-process — enough to exhaust a 512MB instance.
// The query must stay bounded on BOTH ends.

const { skinFindMany, priceHistoryFindMany, priceHistoryCreateMany, skinPriceUpsert } =
  vi.hoisted(() => ({
    skinFindMany: vi.fn(),
    priceHistoryFindMany: vi.fn(),
    priceHistoryCreateMany: vi.fn(),
    skinPriceUpsert: vi.fn(),
  }))

vi.mock('../db/prisma', () => ({
  prisma: {
    skin: { findMany: skinFindMany },
    priceHistory: { findMany: priceHistoryFindMany, createMany: priceHistoryCreateMany },
    skinPrice: { upsert: skinPriceUpsert },
  },
}))

const { redisDel } = vi.hoisted(() => ({ redisDel: vi.fn() }))
vi.mock('../redis/client', () => ({ redis: { del: redisDel } }))

// Keep the marketplace fetchers offline: an empty payload yields empty price
// maps, so no upserts/history are written and the test isolates the read query.
const { axiosGet } = vi.hoisted(() => ({ axiosGet: vi.fn() }))
vi.mock('axios', () => ({ default: { get: axiosGet } }))

const { populatePrices } = await import('./populatePrices')

const log = { info: vi.fn(), warn: vi.fn(), error: vi.fn() } as any

const TWELVE_HOURS_MS = 12 * 60 * 60 * 1000

describe('populatePrices — 24h-change reference query', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axiosGet.mockResolvedValue({ data: [] })
    skinFindMany.mockResolvedValue([{ id: 'skin-1', marketHashName: 'AK-47 | Redline (Field-Tested)' }])
    priceHistoryFindMany.mockResolvedValue([])
    priceHistoryCreateMany.mockResolvedValue({ count: 0 })
    redisDel.mockResolvedValue(undefined)
  })

  it('bounds the reference-price window on the lower end (has a gte), not just lte', async () => {
    await populatePrices(log)

    expect(priceHistoryFindMany).toHaveBeenCalledTimes(1)
    const where = priceHistoryFindMany.mock.calls[0][0].where

    // The lower bound is what prevents the whole retained table from loading.
    expect(where.timestamp.gte).toBeInstanceOf(Date)
    expect(where.timestamp.lte).toBeInstanceOf(Date)
  })

  it('makes the window exactly 12h wide, ending ~24h ago', async () => {
    const before = Date.now()
    await populatePrices(log)
    const after = Date.now()

    const { gte, lte } = priceHistoryFindMany.mock.calls[0][0].where.timestamp

    // lte is ~24h ago; gte is 12h before that.
    expect(after - lte.getTime()).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000)
    expect(lte.getTime() - gte.getTime()).toBe(TWELVE_HOURS_MS)
    expect(before - gte.getTime()).toBeGreaterThanOrEqual(36 * 60 * 60 * 1000)
  })
})
