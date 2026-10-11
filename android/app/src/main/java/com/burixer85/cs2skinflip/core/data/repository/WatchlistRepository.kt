package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.local.WatchlistDao
import com.burixer85.cs2skinflip.core.data.local.WatchlistEntity
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** GET /prices/batch accepts at most this many skins per call. */
private const val PRICE_BATCH_SIZE = 50

@Singleton
class WatchlistRepository @Inject constructor(
    private val dao: WatchlistDao,
    private val backendApi: CS2BackendApiService,
) {
    // Live 24h change per skin from the last refresh. Kept in memory rather than
    // in Room: the `priceChange24h` column is a snapshot from when the skin was
    // added, and changing its type would need a schema bump — which, under
    // fallbackToDestructiveMigration, wipes every user's watchlist.
    private val liveChanges = MutableStateFlow<Map<String, Double?>>(emptyMap())

    fun getAll(): Flow<List<WatchlistItem>> = combine(dao.getAll(), liveChanges) { list, changes ->
        list.map { it.toDomain(priceChange24h = changes[it.skinId]) }
    }

    /**
     * Pulls current prices for every watched skin. Prices are written back to
     * Room so the last known one survives going offline; the 24h change is only
     * shown while it is live. Throws on network failure — the stored prices
     * stay as they were.
     */
    suspend fun refreshPrices() {
        val entities = dao.getAll().first()
        if (entities.isEmpty()) return

        val prices = entities.map { it.skinId }.distinct()
            .chunked(PRICE_BATCH_SIZE)
            .flatMap { ids -> backendApi.getPricesBatch(ids.joinToString(",")).entries }
            .associate { it.key to it.value }

        for (entity in entities) {
            val lowest = prices[entity.skinId]?.lowestPrice ?: continue
            if (lowest != entity.currentPrice) dao.update(entity.copy(currentPrice = lowest))
        }
        liveChanges.value = prices.mapValues { it.value.priceChange24h }
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

    private fun WatchlistEntity.toDomain(priceChange24h: Double?) = WatchlistItem(
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
        // Column is non-null; the snapshot is never displayed (see liveChanges).
        priceChange24h = priceChange24h ?: 0.0,
        alertEnabled = alertEnabled,
        addedAt = addedAt
    )
}
