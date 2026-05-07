package com.burixer85.cs2skinflip.core.domain.model

data class PortfolioItem(
    val assetId: String,
    val skinId: String,
    val name: String,
    val imageUrl: String,
    val wear: SkinWear?,
    val float: Float?,
    val isStatTrak: Boolean,
    val currentPrice: Double,
    val acquirePrice: Double?,    // null = unknown
    val rarity: SkinRarity
) {
    val profitLoss: Double?
        get() = acquirePrice?.let { currentPrice - it }

    val profitLossPct: Double?
        get() = acquirePrice?.let { (currentPrice - it) / it * 100 }
}

data class Portfolio(
    val steamId: String,
    val username: String,
    val avatarUrl: String,
    val items: List<PortfolioItem>,
    val totalValue: Double = items.sumOf { it.currentPrice },
    val lastUpdated: Long = System.currentTimeMillis()
)
