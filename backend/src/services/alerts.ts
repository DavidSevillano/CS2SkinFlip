import { prisma } from '../db/prisma'
import { PriceService } from './prices'
import { sendAlertNotification } from './fcm'

export interface TriggeredAlert {
  alertId: string
  skinId: string
  skinName: string
  type: string
  targetPrice: number
  currentPrice: number
}

export class AlertService {
  private prices = new PriceService()

  /** Check all active alerts and mark triggered ones. Returns triggered list. */
  async checkAllWithResult(): Promise<TriggeredAlert[]> {
    const active = await prisma.alert.findMany({
      where: { isActive: true, isTriggered: false },
    })

    const triggered: TriggeredAlert[] = []

    for (const alert of active) {
      const skin = await prisma.skin.findUnique({ where: { id: alert.skinId } })
      if (!skin) continue

      const prices = await this.prices.getPricesForSkin(alert.skinId, skin.marketHashName)
      const current = prices.lowestPrice
      if (current === null) continue

      const isTriggered =
        (alert.type === 'BUY_BELOW'  && current <= alert.targetPrice) ||
        (alert.type === 'SELL_ABOVE' && current >= alert.targetPrice)

      if (isTriggered) {
        await prisma.alert.update({
          where: { id: alert.id },
          data: { isTriggered: true, triggeredAt: new Date(), isActive: false },
        })
        triggered.push({
          alertId: alert.id,
          skinId: alert.skinId,
          skinName: skin.name,
          type: alert.type,
          targetPrice: alert.targetPrice,
          currentPrice: current,
        })

        // Send FCM push notification if user has a registered device token
        const user = await prisma.user.findUnique({
          where: { id: alert.userId },
          select: { fcmToken: true },
        })
        if (user?.fcmToken) {
          await sendAlertNotification(
            user.fcmToken,
            skin.name,
            alert.type,
            alert.targetPrice,
            current,
            alert.skinId,
          )
        }
        // TODO: send email via Resend / SendGrid
      }
    }

    return triggered
  }

  /** Fire-and-forget wrapper used by the scheduled job. */
  async checkAll(): Promise<void> {
    await this.checkAllWithResult()
  }
}
