import { describe, it, expect, vi, beforeEach } from 'vitest'

const { queryRaw, alertUpdateMany, userUpdateMany } = vi.hoisted(() => ({
  queryRaw: vi.fn(),
  alertUpdateMany: vi.fn(),
  userUpdateMany: vi.fn(),
}))

vi.mock('../db/prisma', () => ({
  prisma: {
    $queryRaw: queryRaw,
    alert: { updateMany: alertUpdateMany },
    user: { updateMany: userUpdateMany },
  },
}))

const { sendAlertNotification } = vi.hoisted(() => ({ sendAlertNotification: vi.fn() }))
vi.mock('./fcm', () => ({ sendAlertNotification }))

const { AlertService } = await import('./alerts')

/** One armed alert as the JOIN returns it: alert + skin + current quotes + user. */
function armed(over: Record<string, unknown> = {}) {
  return {
    id: 'alert-1',
    userId: 'user-1',
    skinId: 'skin-1',
    type: 'BUY_BELOW',
    targetPrice: 50,
    skinName: 'AK-47 | Redline',
    lowestPrice: 45,
    skinportPrice: 45,
    csgoMarketPrice: 46,
    waxpeerPrice: null,
    fcmToken: 'device-token',
    locale: 'es',
    ...over,
  }
}

describe('AlertService.checkAllWithResult', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    alertUpdateMany.mockResolvedValue({ count: 1 })
    userUpdateMany.mockResolvedValue({ count: 1 })
    sendAlertNotification.mockResolvedValue('sent')
  })

  // It used to cost a skin lookup, a price lookup (Redis + two queries), a user
  // lookup and an update per alert, all sequential.
  it('reads every armed alert with its price and owner in a single query', async () => {
    queryRaw.mockResolvedValue([armed(), armed({ id: 'alert-2', targetPrice: 10 })])

    await new AlertService().checkAllWithResult()

    expect(queryRaw).toHaveBeenCalledTimes(1)
  })

  it('triggers a crossed alert, marks it in one update and notifies in the user\'s language', async () => {
    queryRaw.mockResolvedValue([armed()])

    const triggered = await new AlertService().checkAllWithResult()

    expect(triggered).toEqual([
      expect.objectContaining({ alertId: 'alert-1', skinId: 'skin-1', currentPrice: 45 }),
    ])
    expect(alertUpdateMany).toHaveBeenCalledWith({
      where: { id: { in: ['alert-1'] }, isTriggered: false },
      data: { isTriggered: true, triggeredAt: expect.any(Date), isActive: false },
    })
    expect(sendAlertNotification).toHaveBeenCalledWith(
      expect.objectContaining({ token: 'device-token', locale: 'es', type: 'BUY_BELOW', currentPrice: 45 }),
    )
  })

  it('handles SELL_ABOVE', async () => {
    queryRaw.mockResolvedValue([armed({ type: 'SELL_ABOVE', targetPrice: 40 })])

    expect(await new AlertService().checkAllWithResult()).toHaveLength(1)
  })

  it('leaves an alert whose target has not been crossed', async () => {
    queryRaw.mockResolvedValue([armed({ targetPrice: 40 })])

    expect(await new AlertService().checkAllWithResult()).toEqual([])
    expect(alertUpdateMany).not.toHaveBeenCalled()
    expect(sendAlertNotification).not.toHaveBeenCalled()
  })

  // A price only one marketplace quotes is one listing nobody can check; firing
  // on it is how a single absurd ask woke users up for nothing.
  it('does not fire on a price only one marketplace quotes', async () => {
    queryRaw.mockResolvedValue([armed({ skinportPrice: 45, csgoMarketPrice: null, waxpeerPrice: null })])

    expect(await new AlertService().checkAllWithResult()).toEqual([])
  })

  it('does not fire when the marketplaces disagree wildly', async () => {
    queryRaw.mockResolvedValue([armed({ skinportPrice: 45, csgoMarketPrice: 400 })])

    expect(await new AlertService().checkAllWithResult()).toEqual([])
  })

  it('still marks the alert triggered when the user has no device token', async () => {
    queryRaw.mockResolvedValue([armed({ fcmToken: null })])

    const triggered = await new AlertService().checkAllWithResult()

    expect(triggered).toHaveLength(1)
    expect(alertUpdateMany).toHaveBeenCalled()
    expect(sendAlertNotification).not.toHaveBeenCalled()
  })

  // An uninstalled app leaves its token behind; every later alert would retry it.
  it('forgets a device token FCM reports as dead', async () => {
    queryRaw.mockResolvedValue([armed()])
    sendAlertNotification.mockResolvedValue('invalid-token')

    await new AlertService().checkAllWithResult()

    expect(userUpdateMany).toHaveBeenCalledWith({
      where: { id: 'user-1', fcmToken: 'device-token' },
      data: { fcmToken: null },
    })
  })

  it('keeps the token on a transient failure', async () => {
    queryRaw.mockResolvedValue([armed()])
    sendAlertNotification.mockResolvedValue('failed')

    await new AlertService().checkAllWithResult()

    expect(userUpdateMany).not.toHaveBeenCalled()
  })
})
