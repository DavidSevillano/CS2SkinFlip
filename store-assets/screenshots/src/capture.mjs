// html/{locale}/*.html -> out/{locale}/*.png a 2x.
// Espera a las imagenes remotas de Steam y al auto-fit antes de disparar.
import { readdirSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import puppeteer from 'puppeteer'
import { LOCALES } from './locales.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const HTML = join(__dirname, 'html')
const OUT = join(__dirname, '../out')

const SIZES = {
  'feature-graphic-v2': { width: 1024, height: 500 },
  default:              { width: 540,  height: 960 },
}
const sizeFor = (name) => SIZES[name] ?? SIZES.default

async function capturePage(page, file, dir, outDir) {
  const name = file.replace(/\.html$/, '')
  const { width, height } = sizeFor(name)

  await page.setViewport({ width, height, deviceScaleFactor: 2 })
  await page.goto(pathToFileURL(join(dir, file)).href, { waitUntil: 'networkidle0', timeout: 60_000 })

  // Las imagenes de skins vienen de community.akamai.steamstatic.com: hay que
  // esperar a que decodifiquen, no solo a que la request termine.
  await page.evaluate(() => Promise.all(
    [...document.images].map(img => img.complete ? img.decode().catch(() => {}) : new Promise(r => { img.onload = img.onerror = r }))
  ))

  // Una imagen rota no hace fallar a Puppeteer: el decode() de arriba traga el
  // error y el screenshot sale con un hueco. naturalWidth===0 es la unica senal.
  // Cubre tanto que Steam cambie las URLs como que ../../icon.png se rompa.
  const broken = await page.evaluate(() =>
    [...document.images].filter(i => !i.complete || i.naturalWidth === 0).map(i => i.src.slice(0, 90))
  )
  if (broken.length) throw new Error(`Imagenes rotas en ${outDir}/${name}: ${broken.join(' | ')}`)

  await page.waitForSelector('html[data-fit-done="1"]', { timeout: 10_000 })

  // Assert: nada desborda tras el auto-fit.
  const overflow = await page.evaluate(() => {
    const bad = []
    for (const el of document.querySelectorAll('[data-fit-min]')) {
      const box = el.parentElement
      if (el.scrollWidth > box.clientWidth + 1 || el.scrollHeight > box.clientHeight + 1) {
        bad.push(el.className + ': ' + el.textContent.trim().slice(0, 40))
      }
    }
    // Los labels de la bottom nav no tienen auto-fit (encogerlos por idioma se
    // veria inconsistente). .navitem es flex:1 pero min-width:auto impide que
    // encoja por debajo de su contenido, asi que un label largo empuja los 4
    // items mas alla del ancho de .bottomnav y el ultimo se corta. Hay que medir
    // el contenedor: el span del label se encoge al contenido y nunca desborda.
    const nav = document.querySelector('.bottomnav')
    if (nav && nav.scrollWidth > nav.clientWidth + 1) {
      bad.push('bottomnav: ' + nav.textContent.trim().replace(/\s+/g, ' '))
    }
    return bad
  })
  if (overflow.length) throw new Error(`Overflow en ${outDir}/${name}: ${overflow.join(' | ')}`)

  const path = join(outDir, `${name}.png`)
  await page.screenshot({ path })
  return { path, expected: [width * 2, height * 2] }
}

const browser = await puppeteer.launch({ headless: 'new' })
const page = await browser.newPage()
rmSync(OUT, { recursive: true, force: true })

let n = 0
try {
  for (const loc of LOCALES) {
    const dir = join(HTML, loc.play)
    const outDir = join(OUT, loc.play)
    mkdirSync(outDir, { recursive: true })

    for (const file of readdirSync(dir).filter(f => f.endsWith('.html'))) {
      await capturePage(page, file, dir, outDir)
      console.log('  ✓', loc.play, file.replace(/\.html$/, ''))
      n++
    }
  }
} finally {
  await browser.close()
}

console.log(`\nCaptured ${n} PNGs to ${OUT}`)
if (n !== LOCALES.length * 7) throw new Error(`Esperaba ${LOCALES.length * 7} PNGs, salieron ${n}`)
