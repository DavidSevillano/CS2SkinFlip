-- AddForeignKey
ALTER TABLE "WatchlistItem" ADD CONSTRAINT "WatchlistItem_skinId_fkey" FOREIGN KEY ("skinId") REFERENCES "Skin"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Alert" ADD CONSTRAINT "Alert_skinId_fkey" FOREIGN KEY ("skinId") REFERENCES "Skin"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PortfolioItem" ADD CONSTRAINT "PortfolioItem_skinId_fkey" FOREIGN KEY ("skinId") REFERENCES "Skin"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

