package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.local.WatchlistDao
import com.burixer85.cs2skinflip.core.data.local.WatchlistEntity
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val dao: WatchlistDao
) {
    fun getAll(): Flow<List<WatchlistItem>> = dao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun isInWatchlist(skinId: String): Boolean = dao.findBySkinId(skinId) != null

    suspend fun add(item: WatchlistItem) {
        dao.insert(item.toEntity())
    }

    suspend fun remove(id: Long) {
        dao.deleteById(id)
    }

    suspend fun removeBySkinId(skinId: String) {
        dao.findBySkinId(skinId)?.let { dao.delete(it) }
    }

    suspend fun toggleAlert(id: Long, enabled: Boolean) {
        dao.getAll().first().find { it.id == id }?.let { entity ->
            dao.update(entity.copy(alertEnabled = enabled))
        }
    }

    private fun WatchlistEntity.toDomain() = WatchlistItem(
        id = id,
        skinId = skinId,
        skinName = skinName,
        skinImageUrl = skinImageUrl,
        targetBuyPrice = targetBuyPrice,
        targetSellPrice = targetSellPrice,
        currentPrice = currentPrice,
        priceChange24h = priceChange24h,
        alertEnabled = alertEnabled,
        addedAt = addedAt
    )

    private fun WatchlistItem.toEntity() = WatchlistEntity(
        id = id,
        skinId = skinId,
        skinName = skinName,
        skinImageUrl = skinImageUrl,
        targetBuyPrice = targetBuyPrice,
        targetSellPrice = targetSellPrice,
        currentPrice = currentPrice,
        priceChange24h = priceChange24h,
        alertEnabled = alertEnabled,
        addedAt = addedAt
    )
}
