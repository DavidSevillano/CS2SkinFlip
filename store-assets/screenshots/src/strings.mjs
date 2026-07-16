import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { RES_DIR, DEFAULT_ANDROID_DIR } from './locales.mjs'

/**
 * Orden importante: las entidades nombradas primero, &amp; al final.
 * Al reves, "&amp;lt;" se convertiria en "<" en vez de en "&lt;".
 */
const unescapeXml = (s) => s
  .replace(/&lt;/g, '<')
  .replace(/&gt;/g, '>')
  .replace(/&quot;/g, '"')
  .replace(/&apos;/g, "'")
  .replace(/\\'/g, "'")
  .replace(/\\"/g, '"')
  .replace(/&amp;/g, '&')

/** Parsea el subconjunto de strings.xml que usamos: <string> y <plurals>. */
export function parseStrings(xml) {
  const strings = {}
  const plurals = {}

  // Recorta los string-array para que sus <item> no contaminen nada.
  const cleaned = xml.replace(/<string-array[\s\S]*?<\/string-array>/g, '')

  for (const m of cleaned.matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    strings[m[1]] = unescapeXml(m[2])
  }
  for (const m of cleaned.matchAll(/<plurals name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
    const forms = {}
    for (const i of m[2].matchAll(/<item quantity="([^"]+)"[^>]*>([\s\S]*?)<\/item>/g)) {
      forms[i[1]] = unescapeXml(i[2])
    }
    plurals[m[1]] = forms
  }
  return { strings, plurals }
}

/** Sustituye los placeholders de Android: %1$s posicional y %d/%s simples. */
export function format(tpl, args) {
  let i = 0
  return tpl
    .replace(/%(\d+)\$[sd]/g, (_, n) => String(args[Number(n) - 1]))
    .replace(/%[sd]/g, () => String(args[i++]))
}

const cache = new Map()
const load = (androidDir) => {
  if (!cache.has(androidDir)) {
    cache.set(androidDir, parseStrings(readFileSync(join(RES_DIR, androidDir, 'strings.xml'), 'utf8')))
  }
  return cache.get(androidDir)
}

/**
 * Devuelve { t, plural } para un locale, con fallback al ingles.
 * Lanza si una clave no existe en ningun sitio: preferimos romper la build
 * a emitir un PNG con un hueco.
 */
export function createT(locale) {
  const base = load(DEFAULT_ANDROID_DIR)
  const loc = locale.android === DEFAULT_ANDROID_DIR ? base : load(locale.android)

  return {
    t(key, ...args) {
      const v = loc.strings[key] ?? base.strings[key]
      if (v === undefined) throw new Error(`Missing string: ${key} (${locale.android})`)
      return args.length ? format(v, args) : v
    },
    plural(key, n) {
      const forms = loc.plurals[key] ?? base.plurals[key]
      if (!forms) throw new Error(`Missing plural: ${key} (${locale.android})`)
      const cat = new Intl.PluralRules(locale.bcp47).select(n)
      const tpl = forms[cat] ?? forms.other
      if (tpl === undefined) throw new Error(`Missing plural form "${cat}" for ${key} (${locale.android})`)
      return format(tpl, [n])
    },
  }
}
