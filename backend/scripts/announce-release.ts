/**
 * Push a one-off "new version" notification to every user with an FCM token,
 * in their language.
 *
 * DRY RUN by default: prints how many users would get it in each language and
 * the text, and sends nothing. Run it only once the release is live for 100%
 * of users, or people tap through to a Play Store that doesn't offer it yet.
 *
 *   npx tsx scripts/announce-release.ts                          # dry run
 *   TEST_STEAM_ID=7656... SEND=1 npx tsx scripts/announce-release.ts  # just that user
 *   SEND=1 npx tsx scripts/announce-release.ts                   # everyone, once
 *
 * A full send is recorded in Redis (`announce:release:<VERSION>`) and refuses
 * to run twice. Tokens FCM reports dead are cleared, as the alert check does.
 *
 * Production cost: one read of id/fcmToken/locale per user with a token (a few
 * hundred bytes each) plus one small update per dead token.
 *
 * Users whose app predates 1.2.0 never sent a locale, so they get FALLBACK_LOCALE
 * (default English) — and they are the ones this is for.
 */
import 'dotenv/config'
import { prisma } from '../src/db/prisma'
import { redis } from '../src/redis/client'
import { sendPlainNotification } from '../src/services/fcm'
import { normalizeLocale, SUPPORTED_LOCALES, type AlertLocale } from '../src/services/alertMessages'

const VERSION = '1.2'

const TEXT: Record<AlertLocale, { title: string; body: string }> = {
  en: {
    title: `CS2 SkinFlip ${VERSION} is here`,
    body: 'CSFloat prices, a redesigned search and alerts in your language. Update from Google Play.',
  },
  es: {
    title: `Ya está aquí CS2 SkinFlip ${VERSION}`,
    body: 'Precios de CSFloat, búsqueda renovada y alertas en tu idioma. Actualiza desde Google Play.',
  },
  'pt-BR': {
    title: `CS2 SkinFlip ${VERSION} chegou`,
    body: 'Preços da CSFloat, busca renovada e alertas no seu idioma. Atualize pelo Google Play.',
  },
  ru: {
    title: `Вышла CS2 SkinFlip ${VERSION}`,
    body: 'Цены CSFloat, новый поиск и оповещения на вашем языке. Обновите в Google Play.',
  },
  pl: {
    title: `CS2 SkinFlip ${VERSION} już jest`,
    body: 'Ceny z CSFloat, nowe wyszukiwanie i alerty w Twoim języku. Zaktualizuj w Google Play.',
  },
  tr: {
    title: `CS2 SkinFlip ${VERSION} çıktı`,
    body: "CSFloat fiyatları, yenilenen arama ve kendi dilinde uyarılar. Google Play'den güncelle.",
  },
}

const SEND = process.env.SEND === '1'
const TEST_STEAM_ID = process.env.TEST_STEAM_ID
const FALLBACK_LOCALE = normalizeLocale(process.env.FALLBACK_LOCALE ?? 'en')
const SENT_KEY = `announce:release:${VERSION}`

async function main() {
  const users = await prisma.user.findMany({
    where: { fcmToken: { not: null }, ...(TEST_STEAM_ID ? { steamId: TEST_STEAM_ID } : {}) },
    select: { id: true, fcmToken: true, locale: true },
  })
  const localeOf = (u: { locale: string | null }) => (u.locale ? normalizeLocale(u.locale) : FALLBACK_LOCALE)

  console.log(`${users.length} user(s) with a token${TEST_STEAM_ID ? ` (only steamId ${TEST_STEAM_ID})` : ''}`)
  console.log(`  no locale stored (app before 1.2.0) → ${FALLBACK_LOCALE}: ${users.filter((u) => !u.locale).length}`)
  for (const locale of SUPPORTED_LOCALES) {
    const n = users.filter((u) => localeOf(u) === locale).length
    if (n > 0) console.log(`  ${locale}: ${n} — "${TEXT[locale].title}" / "${TEXT[locale].body}"`)
  }

  if (!SEND) {
    console.log('\nDry run: nothing sent. SEND=1 to send.')
    return
  }
  if (!TEST_STEAM_ID && !(await redis.set(SENT_KEY, new Date().toISOString(), { nx: true }))) {
    console.log(`\nAlready sent (${SENT_KEY} = ${await redis.get(SENT_KEY)}). Not sending again.`)
    return
  }

  const tally = { sent: 0, 'invalid-token': 0, failed: 0, disabled: 0 }
  for (const u of users) {
    const { title, body } = TEXT[localeOf(u)]
    const result = await sendPlainNotification(u.fcmToken!, title, body)
    tally[result]++
    if (result === 'invalid-token') {
      await prisma.user.updateMany({ where: { id: u.id, fcmToken: u.fcmToken }, data: { fcmToken: null } })
    }
  }
  console.log('\n', tally)
  if (tally.disabled > 0) {
    console.log('FCM is not configured (FCM_SERVICE_ACCOUNT_PATH) — nothing went out.')
    if (!TEST_STEAM_ID && tally.sent === 0) await redis.del(SENT_KEY)
  }
}

main()
  .catch((e) => {
    console.error(e)
    process.exitCode = 1
  })
  .finally(() => prisma.$disconnect())
