-- CreateEnum
CREATE TYPE "AlertType" AS ENUM ('BUY_BELOW', 'SELL_ABOVE');

-- CreateTable
CREATE TABLE "Alert" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "skinId" TEXT NOT NULL,
    "type" "AlertType" NOT NULL,
    "targetPrice" DOUBLE PRECISION NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "isTriggered" BOOLEAN NOT NULL DEFAULT false,
    "triggeredAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Alert_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PortfolioItem" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "skinId" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "float" DOUBLE PRECISION,
    "acquirePrice" DOUBLE PRECISION NOT NULL,
    "acquiredAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PortfolioItem_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PriceHistory" (
    "id" TEXT NOT NULL,
    "skinId" TEXT NOT NULL,
    "price" DOUBLE PRECISION NOT NULL,
    "source" TEXT NOT NULL DEFAULT 'steam',
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PriceHistory_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Purchase" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "purchaseToken" TEXT NOT NULL,
    "orderId" TEXT,
    "revokedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Purchase_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Skin" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "marketHashName" TEXT NOT NULL,
    "weapon" TEXT NOT NULL,
    "rarity" TEXT NOT NULL,
    "iconUrl" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Skin_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SkinPrice" (
    "id" TEXT NOT NULL,
    "skinId" TEXT NOT NULL,
    "skinportPrice" DOUBLE PRECISION,
    "csgoMarketPrice" DOUBLE PRECISION,
    "lowestPrice" DOUBLE PRECISION,
    "volume24h" INTEGER,
    "priceChange24h" DOUBLE PRECISION,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "waxpeerPrice" DOUBLE PRECISION,

    CONSTRAINT "SkinPrice_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "steamId" TEXT NOT NULL,
    "username" TEXT NOT NULL,
    "avatarUrl" TEXT,
    "isPremium" BOOLEAN NOT NULL DEFAULT false,
    "premiumUntil" TIMESTAMP(3),
    "fcmToken" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "locale" TEXT,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WatchlistItem" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "skinId" TEXT NOT NULL,
    "targetBuyPrice" DOUBLE PRECISION,
    "targetSellPrice" DOUBLE PRECISION,
    "alertEnabled" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WatchlistItem_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Alert_isActive_idx" ON "Alert"("isActive" ASC);

-- CreateIndex
CREATE INDEX "Alert_userId_idx" ON "Alert"("userId" ASC);

-- CreateIndex
CREATE UNIQUE INDEX "PortfolioItem_userId_assetId_key" ON "PortfolioItem"("userId" ASC, "assetId" ASC);

-- CreateIndex
CREATE INDEX "PortfolioItem_userId_idx" ON "PortfolioItem"("userId" ASC);

-- CreateIndex
CREATE INDEX "PriceHistory_skinId_timestamp_idx" ON "PriceHistory"("skinId" ASC, "timestamp" ASC);

-- CreateIndex
CREATE INDEX "PriceHistory_timestamp_idx" ON "PriceHistory"("timestamp" ASC);

-- CreateIndex
CREATE UNIQUE INDEX "Purchase_purchaseToken_key" ON "Purchase"("purchaseToken" ASC);

-- CreateIndex
CREATE INDEX "Purchase_userId_idx" ON "Purchase"("userId" ASC);

-- CreateIndex
CREATE UNIQUE INDEX "Skin_marketHashName_key" ON "Skin"("marketHashName" ASC);

-- CreateIndex
CREATE INDEX "Skin_rarity_idx" ON "Skin"("rarity" ASC);

-- CreateIndex
CREATE INDEX "Skin_weapon_idx" ON "Skin"("weapon" ASC);

-- CreateIndex
CREATE INDEX "SkinPrice_lowestPrice_idx" ON "SkinPrice"("lowestPrice" ASC);

-- CreateIndex
CREATE INDEX "SkinPrice_priceChange24h_idx" ON "SkinPrice"("priceChange24h" DESC);

-- CreateIndex
CREATE UNIQUE INDEX "SkinPrice_skinId_key" ON "SkinPrice"("skinId" ASC);

-- CreateIndex
CREATE INDEX "User_steamId_idx" ON "User"("steamId" ASC);

-- CreateIndex
CREATE UNIQUE INDEX "User_steamId_key" ON "User"("steamId" ASC);

-- CreateIndex
CREATE INDEX "WatchlistItem_userId_idx" ON "WatchlistItem"("userId" ASC);

-- CreateIndex
CREATE UNIQUE INDEX "WatchlistItem_userId_skinId_key" ON "WatchlistItem"("userId" ASC, "skinId" ASC);

-- AddForeignKey
ALTER TABLE "Alert" ADD CONSTRAINT "Alert_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PortfolioItem" ADD CONSTRAINT "PortfolioItem_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PriceHistory" ADD CONSTRAINT "PriceHistory_skinId_fkey" FOREIGN KEY ("skinId") REFERENCES "Skin"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Purchase" ADD CONSTRAINT "Purchase_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SkinPrice" ADD CONSTRAINT "SkinPrice_skinId_fkey" FOREIGN KEY ("skinId") REFERENCES "Skin"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WatchlistItem" ADD CONSTRAINT "WatchlistItem_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

