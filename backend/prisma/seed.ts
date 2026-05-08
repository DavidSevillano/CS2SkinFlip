import { PrismaClient } from '@prisma/client'

const prisma = new PrismaClient()

async function main() {
  console.log('🧹 Clearing manually seeded skins...')

  // Delete in order to respect foreign keys
  await prisma.priceHistory.deleteMany({})
  await prisma.skinPrice.deleteMany({})
  await prisma.skin.deleteMany({})

  console.log('✅ DB cleared — real skins will be imported from Steam Market on next server start')
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e)
    process.exit(1)
  })
  .finally(() => prisma.$disconnect())
