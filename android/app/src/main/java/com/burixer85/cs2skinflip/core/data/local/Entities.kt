package com.burixer85.cs2skinflip.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skinId: String,
    val skinName: String,
    val skinImageUrl: String,
    val targetBuyPrice: Double?,
    val targetSellPrice: Double?,
    val currentPrice: Double,
    val priceChange24h: Double,
    val alertEnabled: Boolean,
    val addedAt: Long
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skinId: String,
    val skinName: String,
    val skinImageUrl: String,
    val type: String,          // AlertType.name
    val targetPrice: Double,
    val currentPrice: Double,
    val isActive: Boolean,
    val isTriggered: Boolean,
    val createdAt: Long,
    val triggeredAt: Long?
)
