import { describe, it, expect } from 'vitest'
import { alertNotificationText, normalizeLocale } from './alertMessages'

describe('alertNotificationText', () => {
  it('writes the notification in the user\'s language', () => {
    expect(alertNotificationText('es', 'BUY_BELOW', 'AK-47 | Redline', 30, 27.5)).toEqual({
      title: 'Alerta de precio: AK-47 | Redline',
      body: 'Ha bajado a $27.50, por debajo de tu objetivo de $30.00',
    })
  })

  it('distinguishes a drop from a rise', () => {
    expect(alertNotificationText('en', 'SELL_ABOVE', 'AWP | Asiimov', 100, 112).body).toBe(
      'Rose to $112.00 — above your $100.00 target',
    )
  })

  // App versions that predate sending the locale still get a notification.
  it('falls back to English when no locale is known', () => {
    expect(alertNotificationText(null, 'BUY_BELOW', 'X', 1, 1).title).toBe('Price alert: X')
  })
})

describe('normalizeLocale', () => {
  it.each([
    ['es-ES', 'es'],
    ['pt', 'pt-BR'],
    ['pt_BR', 'pt-BR'],
    ['RU', 'ru'],
    ['de', 'en'],
    ['', 'en'],
  ])('maps %s to %s', (raw, expected) => {
    expect(normalizeLocale(raw)).toBe(expected)
  })
})
