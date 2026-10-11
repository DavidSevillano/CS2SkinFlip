package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.AssetDto
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.DescriptionDto
import com.burixer85.cs2skinflip.core.data.remote.InventoryResponse
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
import com.burixer85.cs2skinflip.core.data.remote.PortfolioSyncResponseDto
import com.burixer85.cs2skinflip.core.data.remote.SteamApiService
import com.burixer85.cs2skinflip.core.data.remote.SyncPortfolioRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PortfolioRepositoryTest {

    private val backendApi: CS2BackendApiService = mock()
    private val steamApi: SteamApiService = mock()

    private fun inventoryPage(vararg assetIds: String, moreItems: Boolean, lastAssetId: String? = null) = InventoryResponse(
        success = 1,
        assets = assetIds.map { AssetDto(assetid = it, classid = "c$it", instanceid = "0", amount = "1") },
        descriptions = assetIds.map { DescriptionDto("c$it", "0", "Skin $it (Field-Tested)", "Skin $it", "", null) },
        total_inventory_count = null,
        more_items = if (moreItems) 1 else null,
        last_assetid = lastAssetId,
    )

    // Steam splits a large inventory across pages; stopping at the first one
    // silently dropped everything after it from the portfolio.
    @Test
    fun `sync follows every inventory page before sending it`() = runTest {
        whenever(backendApi.getMe()).thenReturn(MeResponseDto("u", "765", "n", null, false, null))
        whenever(steamApi.getPublicInventory(any(), any(), any(), anyOrNull())).thenReturn(
            inventoryPage("1", "2", moreItems = true, lastAssetId = "2"),
            inventoryPage("3", moreItems = false),
        )
        whenever(backendApi.syncPortfolio(any())).thenReturn(PortfolioSyncResponseDto(synced = 3, total = 3))

        PortfolioRepository(backendApi, steamApi).sync()

        val starts = argumentCaptor<String>()
        verify(steamApi, org.mockito.kotlin.times(2)).getPublicInventory(any(), any(), any(), starts.capture())
        assertEquals(listOf(null, "2"), starts.allValues)
        val sent = argumentCaptor<SyncPortfolioRequest>()
        verify(backendApi).syncPortfolio(sent.capture())
        assertEquals(listOf("1", "2", "3"), sent.firstValue.items.map { it.assetId })
    }
}
