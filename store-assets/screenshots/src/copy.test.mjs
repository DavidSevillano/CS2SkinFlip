import { test } from 'node:test'
import assert from 'node:assert/strict'
import { COPY, c } from './copy.mjs'
import { LOCALES } from './locales.mjs'

const KEYS = Object.keys(COPY['en-US'])

test('todos los locales tienen exactamente las mismas claves que en-US', () => {
  for (const l of LOCALES) {
    assert.deepEqual(Object.keys(COPY[l.play]).sort(), [...KEYS].sort(), `mismatch en ${l.play}`)
  }
})

test('ningun valor esta vacio', () => {
  for (const l of LOCALES) {
    for (const k of KEYS) {
      assert.ok(COPY[l.play][k].length > 0, `${l.play}.${k} vacio`)
    }
  }
})

test('los headlines conservan el span de resaltado', () => {
  for (const l of LOCALES) {
    for (const k of KEYS.filter(k => k.endsWith('_headline'))) {
      assert.match(COPY[l.play][k], /<span class="hl">/, `${l.play}.${k} sin resaltado`)
    }
  }
})

test('c() lanza con clave desconocida', () => {
  assert.throws(() => c('ru-RU', 'no_existe'), /Missing copy/)
})
