package com.burixer85.cs2skinflip.core.domain.model

data class Alert(
    val id: String = "",
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
        get() = if (targetPrice > 0) (currentPrice - targetPrice) / targetPrice * 100 else 0.0
}

enum class AlertType(val displayName: String, val description: String) {
    BUY_BELOW("Drops below",  "Notify when price drops below target"),
    SELL_ABOVE("Rises above", "Notify when price rises above target")
}
