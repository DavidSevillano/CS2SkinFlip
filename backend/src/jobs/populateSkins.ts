import axios from 'axios'
import { prisma } from '../db/prisma'
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

export async function populateSkins(log: FastifyBaseLogger): Promise<void> {
  const existing = await prisma.skin.count()
  // Threshold accounts for base skins (~9114) + StatTrak variants (~4500)
  if (existing >= 12000) {
    log.info(`[PopulateSkins] DB already has ${existing} skins, skipping`)
    return
  }

  log.info('[PopulateSkins] Fetching full CS2 skin catalog...')

  const skinsRes = await axios.get<BymykelSkin[]>(BYMYKEL_SKINS_URL, { timeout: 30000 })
  const allSkins: BymykelSkin[] = skinsRes.data

  log.info(`[PopulateSkins] ${allSkins.length} skins from catalog`)

  // Build one DB record per skin × wear combination
  const records: Array<{
    id: string
    marketHashName: string
    weapon: string
    rarity: string
    iconUrl: string
  }> = []

  for (const skin of allSkins) {
    if (!skin.wears || skin.wears.length === 0) continue

    for (const wear of skin.wears) {
      // Market hash name: "{skin.name} ({wear.name})"
      // Knife names start with "★ ", StatTrak knife format: "★ StatTrak™ Karambit | ..."
      const baseName = skin.name  // e.g. "AK-47 | Redline" or "★ Karambit | Fade"
      const marketHashName = `${baseName} (${wear.name})`
      const id = slugify(marketHashName)

      records.push({ id, marketHashName, weapon: skin.weapon.name, rarity: skin.rarity.name, iconUrl: skin.image })

      // Also create StatTrak variant if available
      if (skin.stattrak) {
        const stattrakBase = baseName.startsWith('★ ')
          ? `★ StatTrak™ ${baseName.slice(2)}`   // "★ StatTrak™ Karambit | Fade"
          : `StatTrak™ ${baseName}`               // "StatTrak™ AK-47 | Redline"
        const stattrakHashName = `${stattrakBase} (${wear.name})`
        const stattrakId = slugify(stattrakHashName)
        records.push({
          id: stattrakId,
          marketHashName: stattrakHashName,
          weapon: skin.weapon.name,
          rarity: skin.rarity.name,
          iconUrl: skin.image,
        })
      }
    }
  }

  log.info(`[PopulateSkins] Inserting ${records.length} records...`)

  let imported = 0
  let skipped = 0
  const BATCH_SIZE = 50

  for (let i = 0; i < records.length; i += BATCH_SIZE) {
    const batch = records.slice(i, i + BATCH_SIZE)

    await Promise.all(
      batch.map(async (r) => {
        try {
          await prisma.skin.upsert({
            where: { id: r.id },
            update: { iconUrl: r.iconUrl },
            create: {
              id: r.id,
              name: r.marketHashName,
              marketHashName: r.marketHashName,
              weapon: r.weapon,
              rarity: r.rarity,
              iconUrl: r.iconUrl,
            },
          })

          imported++
        } catch {
          skipped++
        }
      })
    )

    if (i % 1000 === 0 && i > 0) {
      log.info(`[PopulateSkins] Progress: ${i}/${records.length}`)
    }
  }

  await invalidateTopMoversCache()
  log.info(`[PopulateSkins] Done — imported: ${imported}, skipped: ${skipped}`)
}
