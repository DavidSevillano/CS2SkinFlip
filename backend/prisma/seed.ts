import { PrismaClient } from '@prisma/client'

const prisma = new PrismaClient()

const SKINS = [
  {
    id: 'ak47-redline-ft',
    name: 'AK-47 | Redline (Field-Tested)',
    marketHashName: 'AK-47 | Redline (Field-Tested)',
    weapon: 'AK-47',
    rarity: 'Classified',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxIQBuf6rZKjpD7euzg5KIk6SiNe6Vx2gD7Zcp3-iZ84vhiQe18kpmNGn6I4XEdQ80bVFjCuk_lgA',
    price: 15.20,
    priceYesterday: 14.50,
    volume: 1200,
  },
  {
    id: 'awp-asiimov-ft',
    name: 'AWP | Asiimov (Field-Tested)',
    marketHashName: 'AWP | Asiimov (Field-Tested)',
    weapon: 'AWP',
    rarity: 'Covert',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJQAHv7_zLjJB7ODYfoOKlvT0PvXekmoDucJwi77D89ij3Va3_0VhNjz7d4KRdQ0_M1vSqwe1EQ',
    price: 68.50,
    priceYesterday: 71.00,
    volume: 450,
  },
  {
    id: 'm4a4-howl-ft',
    name: 'M4A4 | Howl (Field-Tested)',
    marketHashName: 'M4A4 | Howl (Field-Tested)',
    weapon: 'M4A4',
    rarity: 'Contraband',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJQBcf6rrFjJD_NuLl7WCkL-gNe6Vx2oD7MIpjrqS8ty8iBDo_UssZGylIoaIdVhm0A',
    price: 1850.00,
    priceYesterday: 1790.00,
    volume: 8,
  },
  {
    id: 'karambit-doppler-fn',
    name: 'Karambit | Doppler (Factory New)',
    marketHashName: '★ Karambit | Doppler (Factory New)',
    weapon: 'Karambit',
    rarity: 'Covert',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJAAVfw6KHTNWuoTqXTiJheHJkILSmWwAvDoqMZm88sqk0FG3vBQ9EQ',
    price: 680.00,
    priceYesterday: 650.00,
    volume: 22,
  },
  {
    id: 'awp-dragon-lore-ft',
    name: 'AWP | Dragon Lore (Field-Tested)',
    marketHashName: 'AWP | Dragon Lore (Field-Tested)',
    weapon: 'AWP',
    rarity: 'Covert',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJQAHv7_eLjJD_NuLl7WCkL-gNe6Vx2oD7MIpjrqS8ty8iBDo_UsZWylIoaIdVhm0A',
    price: 2100.00,
    priceYesterday: 2050.00,
    volume: 5,
  },
  {
    id: 'glock18-fade-fn',
    name: 'Glock-18 | Fade (Factory New)',
    marketHashName: 'Glock-18 | Fade (Factory New)',
    weapon: 'Glock-18',
    rarity: 'Restricted',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxIQBuf6rZKjlD7euzg5KIk6SiNr_vm2wI7JAlsv_ujZ94mwiFmLvRsGVjgbo5RJ',
    price: 310.00,
    priceYesterday: 325.00,
    volume: 35,
  },
  {
    id: 'm4a1s-knight-fn',
    name: 'M4A1-S | Knight (Factory New)',
    marketHashName: 'M4A1-S | Knight (Factory New)',
    weapon: 'M4A1-S',
    rarity: 'Covert',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJQAHv7_eLjJD_NuLl7WCkL-gNe6Vx2oD7MIpjrqS8ty8iBDo_UsZWylIoaIdVhm0A',
    price: 420.00,
    priceYesterday: 405.00,
    volume: 18,
  },
  {
    id: 'deagle-blaze-fn',
    name: 'Desert Eagle | Blaze (Factory New)',
    marketHashName: 'Desert Eagle | Blaze (Factory New)',
    weapon: 'Desert Eagle',
    rarity: 'Restricted',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJAAVfw6KHTNWuoTqXTiJheHJkILSmWwAvDoqMZm88sqk0FG3vBQ9EQ',
    price: 175.00,
    priceYesterday: 168.00,
    volume: 60,
  },
  {
    id: 'p250-asiimov-ft',
    name: 'P250 | Asiimov (Field-Tested)',
    marketHashName: 'P250 | Asiimov (Field-Tested)',
    weapon: 'P250',
    rarity: 'Classified',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxJQAHv7_zLjJB7ODYfoOKlvT0PvXekmoDucJwi77D89ij3Va3_0VhNjz7d4KRdQ0_M1vSqwe1EQ',
    price: 4.80,
    priceYesterday: 5.10,
    volume: 3200,
  },
  {
    id: 'ak47-vulcan-fn',
    name: 'AK-47 | Vulcan (Factory New)',
    marketHashName: 'AK-47 | Vulcan (Factory New)',
    weapon: 'AK-47',
    rarity: 'Covert',
    iconUrl:
      'https://steamcommunity-a.akamaihd.net/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I4D0J5R5AvTx6QokZcIPdQWIF3dENa1phlSmUVnRwXoJ6hdtBSAJ_5ohnbqCxIQBuf6rZKjpD7euzg5KIk6SiNe6Vx2gD7Zcp3-iZ84vhiQe18kpmNGn6I4XEdQ80bVFjCuk_lgA',
    price: 88.00,
    priceYesterday: 82.00,
    volume: 210,
  },
]

async function main() {
  console.log('🌱 Seeding database...')

  const yesterday = new Date(Date.now() - 25 * 60 * 60 * 1000) // 25h ago

  for (const skin of SKINS) {
    // Upsert skin
    await prisma.skin.upsert({
      where: { id: skin.id },
      update: { name: skin.name, iconUrl: skin.iconUrl },
      create: {
        id: skin.id,
        name: skin.name,
        marketHashName: skin.marketHashName,
        weapon: skin.weapon,
        rarity: skin.rarity,
        iconUrl: skin.iconUrl,
      },
    })

    // Upsert current price
    await prisma.skinPrice.upsert({
      where: { skinId: skin.id },
      update: {
        steamPrice: skin.price,
        lowestPrice: skin.price,
        volume24h: skin.volume,
        updatedAt: new Date(),
      },
      create: {
        skinId: skin.id,
        steamPrice: skin.price,
        lowestPrice: skin.price,
        volume24h: skin.volume,
      },
    })

    // Create yesterday's price history entry (needed for 24h change calculation)
    await prisma.priceHistory.create({
      data: {
        skinId: skin.id,
        price: skin.priceYesterday,
        source: 'steam',
        timestamp: yesterday,
      },
    })

    // Create today's price history entry
    await prisma.priceHistory.create({
      data: {
        skinId: skin.id,
        price: skin.price,
        source: 'steam',
        timestamp: new Date(),
      },
    })
  }

  console.log(`✅ Seeded ${SKINS.length} skins with prices and history`)
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
