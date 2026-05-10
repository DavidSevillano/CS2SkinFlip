import { env } from '../config/env'
import fs from 'fs'

// Lazy-initialise Firebase Admin so it only loads when FCM_SERVICE_ACCOUNT_PATH is set
let messaging: import('firebase-admin/messaging').Messaging | null = null

async function getMessaging() {
  if (messaging) return messaging
  if (!env.FCM_SERVICE_ACCOUNT_PATH) return null

  try {
    const { initializeApp, cert, getApps } = await import('firebase-admin/app')
    const { getMessaging: _getMessaging } = await import('firebase-admin/messaging')

    if (getApps().length === 0) {
      const serviceAccount = JSON.parse(fs.readFileSync(env.FCM_SERVICE_ACCOUNT_PATH, 'utf-8'))
      initializeApp({ credential: cert(serviceAccount) })
    }

    messaging = _getMessaging()
    return messaging
  } catch (e) {
    console.warn('[FCM] Firebase Admin init failed:', e)
    return null
  }
}

/**
 * Send a push notification to a specific device via its FCM token.
 * Silently skips if FCM is not configured.
 */
export async function sendAlertNotification(
  fcmToken: string,
  skinName: string,
  type: string,
  targetPrice: number,
  currentPrice: number,
  skinId: string,
): Promise<void> {
  const m = await getMessaging()
  if (!m) return

  const isBuyBelow = type === 'BUY_BELOW'
  const title = `Price Alert: ${skinName}`
  const body = isBuyBelow
    ? `Price dropped to $${currentPrice.toFixed(2)} — below your $${targetPrice.toFixed(2)} target!`
    : `Price rose to $${currentPrice.toFixed(2)} — above your $${targetPrice.toFixed(2)} target!`

  try {
    await m.send({
      token: fcmToken,
      notification: { title, body },
      data: { skinId, type },
      android: {
        priority: 'high',
        notification: { channelId: 'price_alerts' },
      },
      apns: {
        payload: {
          aps: { sound: 'default', badge: 1 },
        },
      },
    })
  } catch (e) {
    console.warn('[FCM] Failed to send notification:', e)
  }
}
