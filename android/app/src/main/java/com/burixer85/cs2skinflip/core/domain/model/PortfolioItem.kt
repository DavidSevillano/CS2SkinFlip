package com.burixer85.cs2skinflip.core.domain.model

data class PortfolioItem(
    val id: String,
    val skinId: String,
    val assetId: String,
    val acquirePrice: Double,
    val skinName: String,
    val skinImageUrl: String,
    val currentPrice: Double,
    val profitLoss: Double,
    val profitLossPct: Double,
)

data class PortfolioSummary(
    val totalValue: Double,
    val totalInvested: Double,
    val totalProfitLoss: Double,
    val totalProfitLossPct: Double,
    val itemCount: Int,
)
