import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

/** res/ de la app Android, fuente de las traducciones de UI */
export const RES_DIR = join(__dirname, '../../../android/app/src/main/res')

/**
 * android: directorio en res/
 * play:    codigo de locale de Play Console (nombre del directorio de salida)
 * bcp47:   tag para Intl.PluralRules
 * alsoUploadAs: locales extra de Play que reusan estos mismos PNG
 */
export const LOCALES = [
  { android: 'values',        play: 'en-US', bcp47: 'en'    },
  { android: 'values-es',     play: 'es-ES', bcp47: 'es',    alsoUploadAs: ['es-419'] },
  { android: 'values-pt-rBR', play: 'pt-BR', bcp47: 'pt-BR' },
  { android: 'values-ru',     play: 'ru-RU', bcp47: 'ru'    },
  { android: 'values-tr',     play: 'tr-TR', bcp47: 'tr'    },
  { android: 'values-pl',     play: 'pl-PL', bcp47: 'pl'    },
]

export const DEFAULT_ANDROID_DIR = 'values'
