import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseStrings, format, createT } from './strings.mjs'

test('parsea strings simples', () => {
  const r = parseStrings('<resources><string name="a">Hola</string></resources>')
  assert.equal(r.strings.a, 'Hola')
})

test('des-escapa &amp; sin romper entidades anidadas', () => {
  const r = parseStrings('<resources><string name="a">P&amp;L</string><string name="b">&amp;lt;</string></resources>')
  assert.equal(r.strings.a, 'P&L')
  assert.equal(r.strings.b, '&lt;')   // &amp;lt; es un & literal seguido de "lt;", NO <
})

test('des-escapa apostrofes de Android', () => {
  const r = parseStrings("<resources><string name=\"a\">can\\'t</string></resources>")
  assert.equal(r.strings.a, "can't")
})

test('parsea las cuatro formas plurales del ruso', () => {
  const r = parseStrings(`<resources><plurals name="p">
    <item quantity="one">%d скин</item>
    <item quantity="few">%d скина</item>
    <item quantity="many">%d скинов</item>
    <item quantity="other">%d скина</item>
  </plurals></resources>`)
  assert.equal(r.plurals.p.many, '%d скинов')
  assert.equal(Object.keys(r.plurals.p).length, 4)
})

test('ignora string-array', () => {
  const r = parseStrings('<resources><string-array name="x"><item>a</item></string-array></resources>')
  assert.deepEqual(r.strings, {})
})

test('format sustituye %1$s posicionalmente', () => {
  assert.equal(format('Current market price: %1$s', ['$44.52']), 'Current market price: $44.52')
})

test('format sustituye %d', () => {
  assert.equal(format('%d skins', [128]), '128 skins')
})

test('t() cae al ingles cuando falta la clave en el locale', () => {
  const t = createT({ android: 'values-ru', bcp47: 'ru' })
  assert.ok(typeof t.t('home_subtitle') === 'string')
  assert.ok(t.t('home_subtitle').length > 0)
})

test('t() lanza si la clave no existe en ningun sitio', () => {
  const t = createT({ android: 'values-ru', bcp47: 'ru' })
  assert.throws(() => t.t('clave_que_no_existe_jamas'), /Missing string/)
})

test('plural() elige la forma "many" del ruso para 128', () => {
  const t = createT({ android: 'values-ru', bcp47: 'ru' })
  assert.equal(t.plural('search_results_count', 128), '128 скинов')
})

test('plural() elige la forma "other" del ingles para 128', () => {
  const t = createT({ android: 'values', bcp47: 'en' })
  assert.equal(t.plural('search_results_count', 128), '128 skins')
})
