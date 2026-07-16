import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { LOCALES, RES_DIR } from './locales.mjs'

test('cubre los 6 idiomas de la app', () => {
  assert.equal(LOCALES.length, 6)
})

test('cada locale apunta a un directorio res que existe', () => {
  for (const l of LOCALES) {
    assert.ok(existsSync(join(RES_DIR, l.android)), `falta ${l.android}`)
  }
})

test('los codigos de Play son unicos', () => {
  const play = LOCALES.map(l => l.play)
  assert.equal(new Set(play).size, play.length)
})

test('cada bcp47 es valido para Intl.PluralRules', () => {
  for (const l of LOCALES) {
    assert.doesNotThrow(() => new Intl.PluralRules(l.bcp47))
  }
})
