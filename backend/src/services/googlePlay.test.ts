import { describe, it, expect, vi, beforeEach } from 'vitest'

const { readFileSync } = vi.hoisted(() => ({ readFileSync: vi.fn() }))
vi.mock('fs', () => ({ default: { readFileSync } }))

const { productsGet, productsAcknowledge } = vi.hoisted(() => ({
  productsGet: vi.fn(),
  productsAcknowledge: vi.fn(),
}))
vi.mock('googleapis', () => ({
  google: {
    auth: { GoogleAuth: vi.fn() },
    androidpublisher: vi.fn(() => ({
      purchases: { products: { get: productsGet, acknowledge: productsAcknowledge } },
    })),
  },
}))

vi.mock('../config/env', () => ({
  env: {
    GOOGLE_PLAY_SERVICE_ACCOUNT_PATH: '/fake/service-account.json',
    GOOGLE_PLAY_PACKAGE_NAME: 'com.burixer85.cs2skinflip',
    PREMIUM_PRODUCT_ID: 'premium_unlimited_alerts',
  },
}))

const { verifyAndAcknowledgePurchase } = await import('./googlePlay')

describe('verifyAndAcknowledgePurchase', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    readFileSync.mockReturnValue('{}')
  })

  it('rejects a purchase that is not in the completed state', async () => {
    productsGet.mockResolvedValue({ data: { purchaseState: 1, acknowledgementState: 1 } })

    const result = await verifyAndAcknowledgePurchase('token-1')

    expect(result).toEqual({ ok: false, reason: 'Purchase is not in a completed state' })
    expect(productsAcknowledge).not.toHaveBeenCalled()
  })

  it('acknowledges the purchase when it has not been acknowledged yet', async () => {
    productsGet.mockResolvedValue({
      data: { purchaseState: 0, acknowledgementState: 0, obfuscatedExternalAccountId: 'user-1' },
    })
    productsAcknowledge.mockResolvedValue({})

    const result = await verifyAndAcknowledgePurchase('token-2')

    expect(result).toEqual({ ok: true, obfuscatedAccountId: 'user-1' })
    expect(productsAcknowledge).toHaveBeenCalledWith({
      packageName: 'com.burixer85.cs2skinflip',
      productId: 'premium_unlimited_alerts',
      token: 'token-2',
      requestBody: {},
    })
  })

  it('does not re-acknowledge a purchase that is already acknowledged', async () => {
    productsGet.mockResolvedValue({
      data: { purchaseState: 0, acknowledgementState: 1, obfuscatedExternalAccountId: 'user-2' },
    })

    const result = await verifyAndAcknowledgePurchase('token-3')

    expect(result).toEqual({ ok: true, obfuscatedAccountId: 'user-2' })
    expect(productsAcknowledge).not.toHaveBeenCalled()
  })

  it('returns obfuscatedAccountId null when Play did not record one', async () => {
    productsGet.mockResolvedValue({ data: { purchaseState: 0, acknowledgementState: 1 } })

    const result = await verifyAndAcknowledgePurchase('token-4')

    expect(result).toEqual({ ok: true, obfuscatedAccountId: null })
  })

  it('fails gracefully when the Google Play API call throws', async () => {
    productsGet.mockRejectedValue(new Error('network down'))

    const result = await verifyAndAcknowledgePurchase('token-5')

    expect(result).toEqual({ ok: false, reason: 'Verification request to Google Play failed' })
  })
})
