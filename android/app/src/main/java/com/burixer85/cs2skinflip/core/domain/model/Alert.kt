package com.burixer85.cs2skinflip.core.domain.model

import androidx.annotation.StringRes
import com.burixer85.cs2skinflip.R

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

enum class AlertType(@StringRes val displayNameRes: Int, @StringRes val descriptionRes: Int) {
    BUY_BELOW(R.string.alert_type_buy_below, R.string.alert_type_buy_below_desc),
    SELL_ABOVE(R.string.alert_type_sell_above, R.string.alert_type_sell_above_desc)
}
