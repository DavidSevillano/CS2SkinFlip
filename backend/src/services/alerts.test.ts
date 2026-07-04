import { describe, it, expect, vi, beforeEach } from 'vitest'

const { alertFindMany, alertUpdate, skinFindUnique, userFindUnique } = vi.hoisted(() => ({
  alertFindMany: vi.fn(),
  alertUpdate: vi.fn(),
  skinFindUnique: vi.fn(),
  userFindUnique: vi.fn(),
}))

vi.mock('../db/prisma', () => ({
  prisma: {
    alert: { findMany: alertFindMany, update: alertUpdate },
    skin: { findUnique: skinFindUnique },
    user: { findUnique: userFindUnique },
  },
}))

const { getPricesForSkin } = vi.hoisted(() => ({ getPricesForSkin: vi.fn() }))
vi.mock('./prices', () => ({
  PriceService: class {
    getPricesForSkin = getPricesForSkin
  },
}))

const { sendAlertNotification } = vi.hoisted(() => ({ sendAlertNotification: vi.fn() }))
vi.mock('./fcm', () => ({ sendAlertNotification }))

const { AlertService } = await import('./alerts')

describe('AlertService.checkAllWithResult', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends an FCM push notification when a BUY_BELOW alert triggers and the user has a token', async () => {
    alertFindMany.mockResolvedValue([
      {
        id: 'alert-1',
        userId: 'user-1',
        skinId: 'skin-1',
        type: 'BUY_BELOW',
        targetPrice: 50,
        isActive: true,
        isTriggered: false,
      },
    ])
    skinFindUnique.mockResolvedValue({ id: 'skin-1', name: 'AK-47 | Redline', marketHashName: 'AK-47 | Redline (Field-Tested)' })
    getPricesForSkin.mockResolvedValue({ lowestPrice: 45 })
    userFindUnique.mockResolvedValue({ fcmToken: 'device-token-123' })
    alertUpdate.mockResolvedValue({})

    const triggered = await new AlertService().checkAllWithResult()

    expect(triggered).toHaveLength(1)
    expect(triggered[0]).toMatchObject({ alertId: 'alert-1', skinId: 'skin-1', currentPrice: 45 })

    expect(alertUpdate).toHaveBeenCalledWith({
      where: { id: 'alert-1' },
      data: { isTriggered: true, triggeredAt: expect.any(Date), isActive: false },
    })

    expect(sendAlertNotification).toHaveBeenCalledTimes(1)
    expect(sendAlertNotification).toHaveBeenCalledWith('device-token-123', 'AK-47 | Redline', 'BUY_BELOW', 50, 45, 'skin-1')
  })

  it('does not send a notification when the user has no registered FCM token', async () => {
    alertFindMany.mockResolvedValue([
      {
        id: 'alert-2',
        userId: 'user-2',
        skinId: 'skin-2',
        type: 'SELL_ABOVE',
        targetPrice: 100,
        isActive: true,
        isTriggered: false,
      },
    ])
    skinFindUnique.mockResolvedValue({ id: 'skin-2', name: 'M4A4 | Howl', marketHashName: 'M4A4 | Howl (Factory New)' })
    getPricesForSkin.mockResolvedValue({ lowestPrice: 120 })
    userFindUnique.mockResolvedValue({ fcmToken: null })
    alertUpdate.mockResolvedValue({})

    const triggered = await new AlertService().checkAllWithResult()

    expect(triggered).toHaveLength(1)
    expect(sendAlertNotification).not.toHaveBeenCalled()
  })

  it('does not trigger or notify when the price has not crossed the target', async () => {
    alertFindMany.mockResolvedValue([
      {
        id: 'alert-3',
        userId: 'user-3',
        skinId: 'skin-3',
        type: 'BUY_BELOW',
        targetPrice: 50,
        isActive: true,
        isTriggered: false,
      },
    ])
    skinFindUnique.mockResolvedValue({ id: 'skin-3', name: 'AWP | Asiimov', marketHashName: 'AWP | Asiimov (Field-Tested)' })
    getPricesForSkin.mockResolvedValue({ lowestPrice: 60 })

    const triggered = await new AlertService().checkAllWithResult()

    expect(triggered).toHaveLength(0)
    expect(alertUpdate).not.toHaveBeenCalled()
    expect(sendAlertNotification).not.toHaveBeenCalled()
  })
})
