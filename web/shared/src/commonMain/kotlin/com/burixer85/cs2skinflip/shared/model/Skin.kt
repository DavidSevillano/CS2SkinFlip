package com.burixer85.cs2skinflip.shared.model

import kotlinx.serialization.Serializable

/** One row of `GET /skins/export` — a skin flattened together with its price. */
@Serializable
data class Skin(
    val id: String,
    val marketHashName: String,
    val weapon: String,
    val rarity: String,
    val iconUrl: String,
    val skinportPrice: Double? = null,
    val csgoMarketPrice: Double? = null,
    val waxpeerPrice: Double? = null,
    val lowestPrice: Double? = null,
    val priceChange24h: Double? = null,
    val updatedAt: String,
)

/** One point of `GET /skins/:id/price-history`. */
@Serializable
data class PriceHistoryPoint(
    val price: Double,
    val timestamp: String,
    val source: String = "steam",
)
