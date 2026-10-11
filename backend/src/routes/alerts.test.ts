import { describe, it, expect, vi, beforeEach } from 'vitest'
import Fastify from 'fastify'
import fastifyJwt from '@fastify/jwt'

const { alertFindFirst, alertUpdate, userFindUnique } = vi.hoisted(() => ({
  alertFindFirst: vi.fn(),
  alertUpdate: vi.fn(),
  userFindUnique: vi.fn(),
}))
vi.mock('../db/prisma', () => ({
  prisma: { alert: { findFirst: alertFindFirst, update: alertUpdate }, user: { findUnique: userFindUnique } },
}))

const { alertRoutes } = await import('./alerts')

const triggered = {
  id: 'alert-1',
  userId: 'user-1',
  skinId: 'skin-1',
  type: 'BUY_BELOW',
  targetPrice: 50,
  isActive: false,
  isTriggered: true,
  triggeredAt: new Date('2026-07-28T02:13:05Z'),
  createdAt: new Date('2026-07-01T00:00:00Z'),
}

async function put(body: object) {
  const app = Fastify()
  await app.register(fastifyJwt, { secret: 'test-secret-at-least-32-characters-long' })
  await app.register(alertRoutes)
  const token = app.jwt.sign({ userId: 'user-1', steamId: 's' })
  return app.inject({
    method: 'PUT',
    url: '/alerts/alert-1',
    headers: { authorization: `Bearer ${token}` },
    payload: body,
  })
}

describe('PUT /alerts/:id', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userFindUnique.mockResolvedValue({ id: 'user-1' })
    alertFindFirst.mockResolvedValue(triggered)
    alertUpdate.mockImplementation(async ({ data }) => ({ ...triggered, ...data }))
  })

  // The check only reads alerts that are active and not triggered; switching a
  // triggered alert back on used to leave it showing as active but never firing.
  it('re-arms a triggered alert the user switches back on', async () => {
    const res = await put({ isActive: true })

    expect(res.statusCode).toBe(200)
    expect(alertUpdate.mock.calls[0][0].data).toMatchObject({ isActive: true, isTriggered: false, triggeredAt: null })
  })

  it('re-arms when the target price changes', async () => {
    await put({ targetPrice: 40 })

    expect(alertUpdate.mock.calls[0][0].data).toMatchObject({ targetPrice: 40, isTriggered: false })
  })

  it('switching an alert off keeps its triggered history', async () => {
    alertFindFirst.mockResolvedValue({ ...triggered, isActive: true })

    await put({ isActive: false })

    const data = alertUpdate.mock.calls[0][0].data
    expect(data.isActive).toBe(false)
    expect(data).not.toHaveProperty('isTriggered')
  })
})
