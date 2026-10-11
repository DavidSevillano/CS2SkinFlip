/**
 * Push notification text for a triggered price alert, in the user's language.
 *
 * The app sends its locale along with the FCM token; app versions that predate
 * that send none and get English. Kept in step with the Android app's six
 * locales (res/values-*), using the same terms its strings use for "price alert".
 */

export const SUPPORTED_LOCALES = ['en', 'es', 'pt-BR', 'ru', 'pl', 'tr'] as const
export type AlertLocale = (typeof SUPPORTED_LOCALES)[number]

type Template = {
  title: (skin: string) => string
  dropped: (current: string, target: string) => string
  rose: (current: string, target: string) => string
}

const TEMPLATES: Record<AlertLocale, Template> = {
  en: {
    title: (skin) => `Price alert: ${skin}`,
    dropped: (current, target) => `Dropped to ${current} — below your ${target} target`,
    rose: (current, target) => `Rose to ${current} — above your ${target} target`,
  },
  es: {
    title: (skin) => `Alerta de precio: ${skin}`,
    dropped: (current, target) => `Ha bajado a ${current}, por debajo de tu objetivo de ${target}`,
    rose: (current, target) => `Ha subido a ${current}, por encima de tu objetivo de ${target}`,
  },
  'pt-BR': {
    title: (skin) => `Alerta de preço: ${skin}`,
    dropped: (current, target) => `Caiu para ${current}, abaixo da sua meta de ${target}`,
    rose: (current, target) => `Subiu para ${current}, acima da sua meta de ${target}`,
  },
  ru: {
    title: (skin) => `Оповещение о цене: ${skin}`,
    dropped: (current, target) => `Цена упала до ${current} — ниже вашей цели ${target}`,
    rose: (current, target) => `Цена выросла до ${current} — выше вашей цели ${target}`,
  },
  pl: {
    title: (skin) => `Alert cenowy: ${skin}`,
    dropped: (current, target) => `Cena spadła do ${current} — poniżej Twojego celu ${target}`,
    rose: (current, target) => `Cena wzrosła do ${current} — powyżej Twojego celu ${target}`,
  },
  tr: {
    title: (skin) => `Fiyat uyarısı: ${skin}`,
    dropped: (current, target) => `${current} seviyesine düştü, ${target} hedefinin altında`,
    rose: (current, target) => `${current} seviyesine yükseldi, ${target} hedefinin üstünde`,
  },
}

/** Maps whatever the app sent (e.g. "es-ES", "pt", "pt-BR") to a supported locale, English otherwise. */
export function normalizeLocale(raw: string | null | undefined): AlertLocale {
  if (!raw) return 'en'
  const tag = raw.trim().replace('_', '-')
  const exact = SUPPORTED_LOCALES.find((l) => l.toLowerCase() === tag.toLowerCase())
  if (exact) return exact
  const language = tag.split('-')[0].toLowerCase()
  if (language === 'pt') return 'pt-BR'
  return SUPPORTED_LOCALES.find((l) => l === language) ?? 'en'
}

const usd = (amount: number) => `$${amount.toFixed(2)}`

export function alertNotificationText(
  locale: string | null | undefined,
  type: 'BUY_BELOW' | 'SELL_ABOVE',
  skinName: string,
  targetPrice: number,
  currentPrice: number,
): { title: string; body: string } {
  const t = TEMPLATES[normalizeLocale(locale)]
  const body = type === 'BUY_BELOW'
    ? t.dropped(usd(currentPrice), usd(targetPrice))
    : t.rose(usd(currentPrice), usd(targetPrice))
  return { title: t.title(skinName), body }
}
