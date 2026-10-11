package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.local.WatchlistDao
import com.burixer85.cs2skinflip.core.data.local.WatchlistEntity
import com.burixer85.cs2skinflip.core.data.remote.BatchSkinPriceDto
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WatchlistRepositoryTest {

    /** In-memory stand-in for the Room DAO. */
    private class FakeWatchlistDao(initial: List<WatchlistEntity>) : WatchlistDao {
        val rows = MutableStateFlow(initial)
        override fun getAll(): Flow<List<WatchlistEntity>> = rows
        override suspend fun findBySkinId(skinId: String) = rows.value.firstOrNull { it.skinId == skinId }
        override suspend fun insert(item: WatchlistEntity): Long { rows.value = rows.value + item; return item.id }
        override suspend fun update(item: WatchlistEntity) { rows.value = rows.value.map { if (it.id == item.id) item else it } }
        override suspend fun delete(item: WatchlistEntity) { rows.value = rows.value.filterNot { it.id == item.id } }
        override suspend fun deleteById(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    }

    private fun entity(id: Long, skinId: String, price: Double, change: Double) = WatchlistEntity(
        id = id, skinId = skinId, skinName = skinId, skinImageUrl = "", targetBuyPrice = null,
        targetSellPrice = null, currentPrice = price, priceChange24h = change, alertEnabled = false, addedAt = 0,
    )

    private fun batch(lowest: Double?, change: Double?) = BatchSkinPriceDto(
        skinportPrice = lowest, csgoMarketPrice = null, waxpeerPrice = null, lowestPrice = lowest, priceChange24h = change,
    )

    private val api: CS2BackendApiService = mock()

    @Test
    fun `refreshing replaces the price frozen when the skin was added`() = runTest {
        val dao = FakeWatchlistDao(listOf(entity(1, "ak", price = 10.0, change = 5.0)))
        whenever(api.getPricesBatch("ak")).thenReturn(mapOf("ak" to batch(lowest = 12.0, change = -1.5)))
        val repo = WatchlistRepository(dao, api)

        repo.refreshPrices()

        val item = repo.getAll().first().single()
        assertEquals(12.0, item.currentPrice, 0.0)
        assertEquals(-1.5, item.priceChange24h!!, 0.0)
        // Persisted, so the last known price survives going offline.
        assertEquals(12.0, dao.rows.value.single().currentPrice, 0.0)
    }

    // The stored change is a snapshot from the day the skin was added; showing it
    // as today's 24h move is what made the old screen misleading.
    @Test
    fun `before a refresh the 24h change is unknown, not the stale snapshot`() = runTest {
        val repo = WatchlistRepository(FakeWatchlistDao(listOf(entity(1, "ak", price = 10.0, change = 5.0))), api)

        assertNull(repo.getAll().first().single().priceChange24h)
    }

    @Test
    fun `shows the preferred marketplace price, or the lowest where it does not list the skin`() = runTest {
        val dao = FakeWatchlistDao(listOf(entity(1, "ak", price = 10.0, change = 0.0), entity(2, "m4", price = 10.0, change = 0.0)))
        whenever(api.getPricesBatch("ak,m4")).thenReturn(
            mapOf(
                "ak" to BatchSkinPriceDto(skinportPrice = 24.9, csgoMarketPrice = 27.6, waxpeerPrice = null, csfloatPrice = 25.2, lowestPrice = 24.9, priceChange24h = null),
                "m4" to BatchSkinPriceDto(skinportPrice = 8.0, csgoMarketPrice = null, waxpeerPrice = null, csfloatPrice = null, lowestPrice = 8.0, priceChange24h = null),
            ),
        )
        val repo = WatchlistRepository(dao, api)

        repo.refreshPrices(DefaultMarketplace.CSGO_MARKET)

        assertEquals(listOf(27.6, 8.0), dao.rows.value.map { it.currentPrice })
    }

    @Test
    fun `asks for at most 50 skins per request`() = runTest {
        val entities = (1..120L).map { entity(it, "s$it", price = 1.0, change = 0.0) }
        whenever(api.getPricesBatch(any())).thenReturn(emptyMap())
        val repo = WatchlistRepository(FakeWatchlistDao(entities), api)

        repo.refreshPrices()

        val sizes = org.mockito.kotlin.argumentCaptor<String>().also {
            verify(api, org.mockito.kotlin.times(3)).getPricesBatch(it.capture())
        }.allValues.map { it.split(",").size }
        assertEquals(listOf(50, 50, 20), sizes)
    }

    @Test
    fun `an empty watchlist makes no request`() = runTest {
        WatchlistRepository(FakeWatchlistDao(emptyList()), api).refreshPrices()

        verify(api, never()).getPricesBatch(any())
    }
}
