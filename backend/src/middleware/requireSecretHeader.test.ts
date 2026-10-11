import { describe, it, expect } from 'vitest'
import Fastify from 'fastify'
import { requireSecretHeader } from './requireSecretHeader'

const SECRET = 'a-secret-that-is-at-least-32-characters'

async function buildTestApp() {
  const app = Fastify()
  let handlerRuns = 0
  await app.register(async (scoped) => {
    scoped.addHook('onRequest', requireSecretHeader('X-Test-Secret', SECRET))
    scoped.get('/guarded', async () => {
      handlerRuns++
      return { ok: true }
    })
  })
  return { app, handlerRuns: () => handlerRuns }
}

describe('requireSecretHeader', () => {
  it('lets a request with the right secret through', async () => {
    const { app, handlerRuns } = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/guarded', headers: { 'x-test-secret': SECRET } })

    expect(res.statusCode).toBe(200)
    expect(handlerRuns()).toBe(1)
  })

  it.each([
    ['no header', {}],
    ['a wrong secret', { 'x-test-secret': 'nope' }],
    ['a prefix of the secret', { 'x-test-secret': SECRET.slice(0, -1) }],
    ['the secret plus extra', { 'x-test-secret': `${SECRET}x` }],
    ['an empty header', { 'x-test-secret': '' }],
  ])('rejects %s without running the handler', async (_label, headers) => {
    const { app, handlerRuns } = await buildTestApp()

    const res = await app.inject({ method: 'GET', url: '/guarded', headers })

    expect(res.statusCode).toBe(401)
    expect(handlerRuns()).toBe(0)
  })
})
