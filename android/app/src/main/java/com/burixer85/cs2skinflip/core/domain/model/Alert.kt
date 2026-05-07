package com.burixer85.cs2skinflip.core.domain.model

data class Alert(
    val id: Long = 0,
    val skinId: String,
    val skinName: String,
    val skinImageUrl: String,
    val type: AlertType,
    val targetPrice: Double,
    val currentPrice: Double,
    val isActive: Boolean = true,
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val triggeredAt: Long? = null
) {
    val percentageDiff: Double
        get() = (currentPrice - targetPrice) / targetPrice * 100
}

enum class AlertType(val displayName: String) {
    BUY_BELOW("Buy below"),
    SELL_ABOVE("Sell above")
}
