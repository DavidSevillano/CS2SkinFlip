import { describe, it, expect } from 'vitest'
import { calcLowestPrice, isCorroborated, lowestPriceAt } from './quotes'
import { CSFLOAT_IN_LOWEST_FROM } from '../config/priceQuality'

const quotes = (skinport: number | null, csgo: number | null, waxpeer: number | null, csfloat: number | null) => ({
  skinportPrice: skinport,
  csgoMarketPrice: csgo,
  waxpeerPrice: waxpeer,
  csfloatPrice: csfloat,
})

describe('calcLowestPrice', () => {
  it('takes the plain minimum with fewer than three quotes', () => {
    expect(calcLowestPrice(10, 400, null)).toBe(10)
  })

  // With four quotes the median sits between the middle two.
  it('drops an absurd quote against the median of four', () => {
    expect(calcLowestPrice(100, 105, 110, 2)).toBe(100)
  })

  it('keeps four quotes that merely spread', () => {
    expect(calcLowestPrice(100, 105, 110, 92)).toBe(92)
  })
})

describe('lowestPriceAt', () => {
  const q = quotes(100, 105, 110, 92)

  // Stored from the first run, counted only from the switch-over.
  it('leaves CSFloat out before CSFLOAT_IN_LOWEST_FROM', () => {
    expect(lowestPriceAt(q, CSFLOAT_IN_LOWEST_FROM - 1)).toBe(100)
  })

  it('counts CSFloat from CSFLOAT_IN_LOWEST_FROM on', () => {
    expect(lowestPriceAt(q, CSFLOAT_IN_LOWEST_FROM)).toBe(92)
  })
})

describe('isCorroborated', () => {
  // CSFloat is a second opinion like any other.
  it('accepts CSFloat as the second agreeing quote', () => {
    expect(isCorroborated(quotes(null, null, 100, 95))).toBe(true)
  })
})
