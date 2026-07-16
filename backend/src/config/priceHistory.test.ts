import { describe, it, expect } from 'vitest'
import { CHANGE_REFERENCE_WINDOW_MS, REFRESH_INTERVAL_MS } from './priceHistory'

// The failure this guards against is silent: nothing throws, no query errors —
// top-movers just goes empty because every skin's `priceChange24h` is null. That
// makes it exactly the kind of thing to pin in a test rather than a comment.
describe('price pipeline timings', () => {
  it('keeps the reference window wide enough to contain a write', () => {
    expect(CHANGE_REFERENCE_WINDOW_MS).toBeGreaterThanOrEqual(REFRESH_INTERVAL_MS)
  })

  it('leaves slack for missed runs, so one failed refresh does not blank the 24h change', () => {
    expect(CHANGE_REFERENCE_WINDOW_MS).toBeGreaterThanOrEqual(REFRESH_INTERVAL_MS * 2)
  })
})
