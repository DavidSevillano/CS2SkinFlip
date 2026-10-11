import { describe, it, expect, vi } from 'vitest'

vi.mock('../db/prisma', () => ({ prisma: {} }))

const { change24h } = await import('./priceReference')
const { CSFLOAT_IN_LOWEST_FROM } = await import('../config/priceQuality')

// Today: CSFloat undercuts the other three by 8% and is in lowestPrice.
const row = { skinportPrice: 100, csgoMarketPrice: 105, waxpeerPrice: 110, csfloatPrice: 92, lowestPrice: 92 }

describe('change24h', () => {
  // The reference was the minimum of three. Comparing it with a minimum of four
  // reads CSFloat joining as an 8% drop on a skin whose price didn't move.
  it('compares a reference from before CSFloat joined without CSFloat', () => {
    const ref = { price: 100, at: new Date(CSFLOAT_IN_LOWEST_FROM - 60 * 60 * 1000) }

    expect(change24h(row, ref)).toBe(0)
  })

  it('compares a reference from after it with lowestPrice', () => {
    const ref = { price: 92, at: new Date(CSFLOAT_IN_LOWEST_FROM + 60 * 60 * 1000) }

    expect(change24h(row, ref)).toBe(0)
  })

  it('is null without a reference', () => {
    expect(change24h(row, undefined)).toBeNull()
  })
})
