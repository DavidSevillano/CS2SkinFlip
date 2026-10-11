import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import { invalidateTopMoversCache } from '../services/prices'
import type { FastifyBaseLogger } from 'fastify'

const BYMYKEL_SKINS_URL =
  'https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/skins.json'

// This import used to also fetch Steam prices from prices.csgotrader.app and
// seed SkinPrice with them. Removed 2026-07-24 for two independent reasons:
//
//  1. The endpoint has stopped serving JSON — it answers 200 with the site's
//     HTML. The failure was invisible: `.catch(() => null)` swallowed nothing
//     (there was no error), and the "N prices loaded" log counted the character
//     indices of the HTML string, so it reported ~36 700 prices every run.
//
//  2. Even working, seeding SkinPrice here broke the bootstrap. `app.ts` runs
//     populatePrices only when SkinPrice is empty; a seeded row made it
//     non-empty, so a brand-new database would skip the marketplace fetch
//     entirely and serve Steam-derived prices until the first cron run hours
//     later. populatePrices writes better data immediately afterwards anyway.

interface BymykelSkin {
  id: string
  name: string             // e.g. "AK-47 | Redline" or "StatTrak™ AK-47 | Redline"
  weapon: { id: string; name: string }
  rarity: { id: string; name: string; color: string }
  stattrak: boolean
  souvenir: boolean
  wears: Array<{ id: string; name: string }>
  image: string
}

function slugify(str: string): string {
  return str
    .toLowerCase()
    .replace(/[★✦™]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .substring(0, 80)
}

interface SkinRecord {
  id: string
  name: string
  marketHashName: string
  weapon: string
  rarity: string
  iconUrl: string
}

export const SOUVENIR_PREFIX = 'Souvenir '

/**
 * One record per skin × wear, plus its StatTrak twin where the skin has one,
 * plus its Souvenir twin where [isListed] says a marketplace quotes it.
 *
 * Souvenirs need the check because the catalog only says a skin has a souvenir
 * version, not in which wears: of 6 825 souvenir skin × wear combinations it
 * implies, marketplaces quoted 2 527 (2026-10-11). The rest would be rows with
 * no price, padding every search. Without [isListed], no souvenirs.
 */
export function catalogRecords(allSkins: BymykelSkin[], isListed?: (marketHashName: string) => boolean): SkinRecord[] {
  const records: SkinRecord[] = []

  for (const skin of allSkins) {
    if (!skin.wears || skin.wears.length === 0) continue

    for (const wear of skin.wears) {
      // Market hash name: "{skin.name} ({wear.name})"
      // Knife names start with "★ ", StatTrak knife format: "★ StatTrak™ Karambit | ..."
      const baseName = skin.name  // e.g. "AK-47 | Redline" or "★ Karambit | Fade"
      const marketHashName = `${baseName} (${wear.name})`
      records.push({
        id: slugify(marketHashName),
        name: marketHashName,
        marketHashName,
        weapon: skin.weapon.name,
        rarity: skin.rarity.name,
        iconUrl: skin.image,
      })

      // Also create StatTrak variant if available
      if (skin.stattrak) {
        const stattrakBase = baseName.startsWith('★ ')
          ? `★ StatTrak™ ${baseName.slice(2)}`   // "★ StatTrak™ Karambit | Fade"
          : `StatTrak™ ${baseName}`               // "StatTrak™ AK-47 | Redline"
        const stattrakHashName = `${stattrakBase} (${wear.name})`
        records.push({
          id: slugify(stattrakHashName),
          name: stattrakHashName,
          marketHashName: stattrakHashName,
          weapon: skin.weapon.name,
          rarity: skin.rarity.name,
          iconUrl: skin.image,
        })
      }

      if (skin.souvenir && isListed) {
        const souvenirHashName = `${SOUVENIR_PREFIX}${marketHashName}`
        if (isListed(souvenirHashName)) {
          records.push({
            id: slugify(souvenirHashName),
            name: souvenirHashName,
            marketHashName: souvenirHashName,
            weapon: skin.weapon.name,
            rarity: skin.rarity.name,
            iconUrl: skin.image,
          })
        }
      }
    }
  }

  return records
}

// 6 columns per row keeps a batch far under Postgres' 65 535 bind parameters.
const INSERT_BATCH_SIZE = 1000

/**
 * Adds any skin in the catalog that the database doesn't have yet. Existing
 * rows are left untouched: `skipDuplicates` is ON CONFLICT DO NOTHING, so an
 * id or marketHashName already present costs no write, and createMany returns
 * only a count, so nothing is read back across Neon's metered transfer.
 */
export async function syncSkinCatalog(
  log: FastifyBaseLogger,
  isListed?: (marketHashName: string) => boolean,
): Promise<{ inserted: number }> {
  const skinsRes = await axios.get<BymykelSkin[]>(BYMYKEL_SKINS_URL, { timeout: 30000 })
  const records = catalogRecords(skinsRes.data, isListed)

  let inserted = 0
  for (let i = 0; i < records.length; i += INSERT_BATCH_SIZE) {
    const { count } = await prisma.skin.createMany({
      data: records.slice(i, i + INSERT_BATCH_SIZE),
      skipDuplicates: true,
    })
    inserted += count
  }

  if (inserted > 0) await invalidateTopMoversCache()
  log.info(`[PopulateSkins] Catalog has ${records.length} records — ${inserted} new`)
  return { inserted }
}

// `pipeline:` rather than `prices:` because app.ts wipes `prices:*` on boot.
export const CATALOG_SYNCED_AT_KEY = 'pipeline:catalog:syncedAt'
// New skins arrive with game updates, weeks apart; a daily look is plenty.
const CATALOG_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000

/**
 * Runs [syncSkinCatalog] at most once a day. Called by populatePrices between
 * fetching the marketplaces and pricing the catalog, so a new skin is priced in
 * the same run and [isListed] can answer from the feeds just fetched.
 * Returns null when skipped.
 */
export async function syncSkinCatalogIfDue(
  log: FastifyBaseLogger,
  isListed?: (marketHashName: string) => boolean,
): Promise<{ inserted: number } | null> {
  const raw = await redis.get(CATALOG_SYNCED_AT_KEY)
  const last = Number(raw)
  if (raw !== null && Number.isFinite(last) && Date.now() - last < CATALOG_SYNC_INTERVAL_MS) return null

  const result = await syncSkinCatalog(log, isListed)
  await redis.set(CATALOG_SYNCED_AT_KEY, Date.now())
  return result
}

/**
 * Bootstrap import for an empty database. Routine additions, souvenirs among
 * them, come from [syncSkinCatalogIfDue].
 */
export async function populateSkins(log: FastifyBaseLogger): Promise<void> {
  const existing = await prisma.skin.count()
  // Threshold accounts for base skins (~9114) + StatTrak variants (~4500)
  if (existing >= 12000) {
    log.info(`[PopulateSkins] DB already has ${existing} skins, skipping`)
    return
  }

  log.info('[PopulateSkins] Fetching full CS2 skin catalog...')
  await syncSkinCatalog(log)
}
