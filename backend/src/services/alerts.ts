import { prisma } from '../db/prisma'
import { PriceService } from './prices'

export class AlertService {
  private prices = new PriceService()

  async checkAll(): Promise<void> {
    const active = await prisma.alert.findMany({
      where: { isActive: true, isTriggered: false },
    })

    for (const alert of active) {
      const skin = await prisma.skin.findUnique({ where: { id: alert.skinId } })
      if (!skin) continue

      const prices = await this.prices.getPricesForSkin(alert.skinId, skin.marketHashName)
      const current = prices.lowestPrice
      if (current === null) continue

      const triggered =
        (alert.type === 'BUY_BELOW' && current <= alert.targetPrice) ||
        (alert.type === 'SELL_ABOVE' && current >= alert.targetPrice)

      if (triggered) {
        await prisma.alert.update({
          where: { id: alert.id },
          data: { isTriggered: true, triggeredAt: new Date(), isActive: false },
        })
        // TODO: send push notification via FCM
        // TODO: send email via Resend / SendGrid
      }
    }
  }
}
