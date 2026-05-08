import axios from 'axios'
import { prisma } from '../db/prisma'
import { redis } from '../redis/client'
import type { FastifyBaseLogger } from 'fastify'

const BYMYKEL_SKINS_URL =
  'https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/skins.json'
const CSGOTRADER_PRICES_URL =
  'https://prices.csgotrader.app/latest/prices.json'

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

interface CSGOTraderPrices {
  [marketHashName: string]: {
    steam?: { last_24h?: number; last_7d?: number; last_30d?: number }
  }
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
  if (existing >= 500) {
    log.info(`[PopulateSkins] DB already has ${existing} skins, skipping`)
    return
  }

  log.info('[PopulateSkins] Fetching full CS2 skin catalog...')

  const [skinsRes, pricesRes] = await Promise.all([
    axios.get<BymykelSkin[]>(BYMYKEL_SKINS_URL, { timeout: 30000 }),
    axios.get<CSGOTraderPrices>(CSGOTRADER_PRICES_URL, { timeout: 30000 }).catch(() => null),
  ])

  const allSkins: BymykelSkin[] = skinsRes.data
  const prices: CSGOTraderPrices = pricesRes?.data ?? {}

  log.info(`[PopulateSkins] ${allSkins.length} skins from catalog, ${Object.keys(prices).length} prices loaded`)

  // Build one DB record per skin × wear combination
  const records: Array<{
    id: string
    marketHashName: string
    weapon: string
    rarity: string
    iconUrl: string
    price: number | null
  }> = []

  for (const skin of allSkins) {
    if (!skin.wears || skin.wears.length === 0) continue

    for (const wear of skin.wears) {
      // Market hash name is exactly: "{skin.name} ({wear.name})"
      const marketHashName = `${skin.name} (${wear.name})`
      const id = slugify(marketHashName)

      const priceData = prices[marketHashName]
      const price =
        priceData?.steam?.last_24h ??
        priceData?.steam?.last_7d ??
        priceData?.steam?.last_30d ??
        null

      records.push({
        id,
        marketHashName,
        weapon: skin.weapon.name,
        rarity: skin.rarity.name,
        iconUrl: skin.image,
        price,
      })
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

          if (r.price !== null) {
            await prisma.skinPrice.upsert({
              where: { skinId: r.id },
              update: { steamPrice: r.price, lowestPrice: r.price, updatedAt: new Date() },
              create: { skinId: r.id, steamPrice: r.price, lowestPrice: r.price },
            })
          }

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

  await redis.del('top-movers:20')
  log.info(`[PopulateSkins] Done — imported: ${imported}, skipped: ${skipped}`)
}
