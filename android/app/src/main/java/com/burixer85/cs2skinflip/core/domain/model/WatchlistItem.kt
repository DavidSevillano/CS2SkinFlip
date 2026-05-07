package com.burixer85.cs2skinflip.core.domain.model

data class WatchlistItem(
    val id: Long = 0,
    val skinId: String,
    val skinName: String,
    val skinImageUrl: String,
    val targetBuyPrice: Double?,
    val targetSellPrice: Double?,
    val currentPrice: Double,
    val priceChange24h: Double,
    val alertEnabled: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    val priceVsTarget: Double?
        get() = targetBuyPrice?.let { (currentPrice - it) / it * 100 }
            ?: targetSellPrice?.let { (currentPrice - it) / it * 100 }

    val isBuyTarget: Boolean get() = targetBuyPrice != null
}
