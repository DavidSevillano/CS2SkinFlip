import { env } from '../config/env'
import fs from 'fs'

// Lazy-initialise the Google Play Developer API client so it only loads when
// GOOGLE_PLAY_SERVICE_ACCOUNT_PATH is set — mirrors the FCM lazy-init pattern.
let androidpublisher: import('googleapis').androidpublisher_v3.Androidpublisher | null = null

async function getClient() {
  if (androidpublisher) return androidpublisher
  if (!env.GOOGLE_PLAY_SERVICE_ACCOUNT_PATH) return null

  try {
    const { google } = await import('googleapis')
    const serviceAccount = JSON.parse(fs.readFileSync(env.GOOGLE_PLAY_SERVICE_ACCOUNT_PATH, 'utf-8'))
    const auth = new google.auth.GoogleAuth({
      credentials: serviceAccount,
      scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    })
    androidpublisher = google.androidpublisher({ version: 'v3', auth })
    return androidpublisher
  } catch (e) {
    console.warn('[GooglePlay] Client init failed:', e)
    return null
  }
}

type VerifyResult =
  | { ok: true; obfuscatedAccountId: string | null }
  | { ok: false; reason: string }

/**
 * Verifies a one-time purchase token against the Google Play Developer API and
 * acknowledges it if needed (Play auto-refunds unacknowledged purchases after 3 days).
 */
export async function verifyAndAcknowledgePurchase(purchaseToken: string): Promise<VerifyResult> {
  const client = await getClient()
  if (!client) return { ok: false, reason: 'Google Play verification is not configured' }

  try {
    const { data } = await client.purchases.products.get({
      packageName: env.GOOGLE_PLAY_PACKAGE_NAME,
      productId: env.PREMIUM_PRODUCT_ID,
      token: purchaseToken,
    })

    // purchaseState: 0 = purchased, 1 = canceled, 2 = pending
    if (data.purchaseState !== 0) {
      return { ok: false, reason: 'Purchase is not in a completed state' }
    }

    // acknowledgementState: 0 = not acknowledged, 1 = acknowledged
    if (data.acknowledgementState === 0) {
      await client.purchases.products.acknowledge({
        packageName: env.GOOGLE_PLAY_PACKAGE_NAME,
        productId: env.PREMIUM_PRODUCT_ID,
        token: purchaseToken,
        requestBody: {},
      })
    }

    return { ok: true, obfuscatedAccountId: data.obfuscatedExternalAccountId ?? null }
  } catch (e) {
    console.warn('[GooglePlay] Verification failed:', e)
    return { ok: false, reason: 'Verification request to Google Play failed' }
  }
}
