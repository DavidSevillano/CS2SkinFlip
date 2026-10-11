import { prisma } from '../db/prisma'
import { sendAlertNotification } from './fcm'
import { isCorroborated } from './quotes'

export interface TriggeredAlert {
  alertId: string
  skinId: string
  skinName: string
  type: string
  targetPrice: number
  currentPrice: number
}

interface ArmedAlertRow {
  id: string
  userId: string
  skinId: string
  type: 'BUY_BELOW' | 'SELL_ABOVE'
  targetPrice: number
  skinName: string
  lowestPrice: number
  skinportPrice: number | null
  csgoMarketPrice: number | null
  waxpeerPrice: number | null
  csfloatPrice: number | null
  fcmToken: string | null
  locale: string | null
}

export class AlertService {
  /**
   * Checks every armed alert against the prices the bulk job just wrote, marks
   * the crossed ones triggered and notifies their owners. Returns what fired.
   *
   * Reads straight from SkinPrice in one query: this runs right after
   * populatePrices, so there is nothing fresher to look up per alert.
   */
  async checkAllWithResult(): Promise<TriggeredAlert[]> {
    const rows = await prisma.$queryRaw<ArmedAlertRow[]>`
      SELECT a."id", a."userId", a."skinId", a."type"::text AS "type", a."targetPrice",
             s."name" AS "skinName",
             sp."lowestPrice", sp."skinportPrice", sp."csgoMarketPrice", sp."waxpeerPrice", sp."csfloatPrice",
             u."fcmToken", u."locale"
      FROM "Alert" a
      JOIN "Skin" s ON s."id" = a."skinId"
      JOIN "SkinPrice" sp ON sp."skinId" = a."skinId"
      JOIN "User" u ON u."id" = a."userId"
      WHERE a."isActive" = true AND a."isTriggered" = false AND sp."lowestPrice" IS NOT NULL
    `

    // Only a corroborated price may fire an alert: one marketplace's lone ask
    // can be anything, and a false alert is worse than a late one.
    const crossed = rows.filter((r) =>
      isCorroborated(r) &&
      ((r.type === 'BUY_BELOW' && r.lowestPrice <= r.targetPrice) ||
       (r.type === 'SELL_ABOVE' && r.lowestPrice >= r.targetPrice)),
    )
    if (crossed.length === 0) return []

    await prisma.alert.updateMany({
      where: { id: { in: crossed.map((r) => r.id) }, isTriggered: false },
      data: { isTriggered: true, triggeredAt: new Date(), isActive: false },
    })

    const results = await Promise.all(
      crossed
        .filter((r) => r.fcmToken)
        .map(async (r) => ({
          row: r,
          result: await sendAlertNotification({
            token: r.fcmToken!,
            locale: r.locale,
            skinName: r.skinName,
            type: r.type,
            targetPrice: r.targetPrice,
            currentPrice: r.lowestPrice,
            skinId: r.skinId,
          }),
        })),
    )

    // A token FCM reports as dead belongs to an uninstalled app; every later
    // alert would retry it. Matching on the token too leaves a fresh one the
    // app registered in the meantime alone.
    for (const { row } of results.filter((x) => x.result === 'invalid-token')) {
      await prisma.user.updateMany({
        where: { id: row.userId, fcmToken: row.fcmToken },
        data: { fcmToken: null },
      })
    }

    return crossed.map((r) => ({
      alertId: r.id,
      skinId: r.skinId,
      skinName: r.skinName,
      type: r.type,
      targetPrice: r.targetPrice,
      currentPrice: r.lowestPrice,
    }))
  }

  /** Fire-and-forget wrapper used by the scheduled job. */
  async checkAll(): Promise<void> {
    await this.checkAllWithResult()
  }
}
