import { env } from '../config/env'
import fs from 'fs'
import { alertNotificationText } from './alertMessages'
import { logger } from '../logger'

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
    logger.warn({ err: e }, '[FCM] Firebase Admin init failed')
    return null
  }
}

/** `invalid-token`: FCM says the device token is gone — the caller should forget it. */
export type SendResult = 'sent' | 'invalid-token' | 'failed' | 'disabled'

// FCM error codes meaning the token will never work again (app uninstalled,
// data cleared, token rotated). Anything else may be transient.
const DEAD_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
])

/**
 * Sends a one-off message with no skin attached (e.g. a release announcement).
 * Same `price_alerts` channel as alerts: it is the only one the app creates.
 */
export async function sendPlainNotification(token: string, title: string, body: string): Promise<SendResult> {
  const m = await getMessaging()
  if (!m) return 'disabled'

  try {
    await m.send({
      token,
      notification: { title, body },
      android: { notification: { channelId: 'price_alerts' } },
    })
    return 'sent'
  } catch (e) {
    const code = (e as { code?: string })?.code
    if (code && DEAD_TOKEN_CODES.has(code)) return 'invalid-token'
    logger.warn({ err: e }, '[FCM] Failed to send notification')
    return 'failed'
  }
}

export interface AlertNotification {
  token: string
  locale: string | null
  skinName: string
  type: 'BUY_BELOW' | 'SELL_ABOVE'
  targetPrice: number
  currentPrice: number
  skinId: string
}

/**
 * Sends a triggered alert to one device, in the user's language.
 *
 * Carries a `notification` payload, not data-only: app versions in the field
 * only display messages that have one (onMessageReceived returns early without
 * it), and with the app in the background the system shows it directly.
 */
export async function sendAlertNotification(n: AlertNotification): Promise<SendResult> {
  const m = await getMessaging()
  if (!m) return 'disabled'

  const { title, body } = alertNotificationText(n.locale, n.type, n.skinName, n.targetPrice, n.currentPrice)

  try {
    await m.send({
      token: n.token,
      notification: { title, body },
      data: { skinId: n.skinId, type: n.type },
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
    return 'sent'
  } catch (e) {
    const code = (e as { code?: string })?.code
    if (code && DEAD_TOKEN_CODES.has(code)) return 'invalid-token'
    logger.warn({ err: e }, '[FCM] Failed to send notification')
    return 'failed'
  }
}
