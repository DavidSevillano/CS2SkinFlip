package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.SteamApiService
import com.burixer85.cs2skinflip.core.data.remote.SteamInventoryPrivateException
import com.burixer85.cs2skinflip.core.data.remote.SyncItemRequest
import com.burixer85.cs2skinflip.core.data.remote.SyncPortfolioRequest
import com.burixer85.cs2skinflip.core.data.remote.UpsertPortfolioItemRequest
import com.burixer85.cs2skinflip.core.data.remote.toDomain
import com.burixer85.cs2skinflip.core.domain.model.PortfolioItem
import com.burixer85.cs2skinflip.core.domain.model.PortfolioSummary
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class PortfolioState(
    val items: List<PortfolioItem>,
    val summary: PortfolioSummary,
)

@Singleton
class PortfolioRepository @Inject constructor(
    private val backendApi: CS2BackendApiService,
    private val steamApiService: SteamApiService,
) {
    suspend fun fetchAll(): PortfolioState {
        val response = backendApi.getPortfolio()
        return PortfolioState(
            items = response.items.map { it.toDomain() },
            summary = PortfolioSummary(
                totalValue = response.summary.totalValue,
                totalInvested = response.summary.totalInvested,
                totalProfitLoss = response.summary.totalProfitLoss,
                totalProfitLossPct = response.summary.totalProfitLossPct,
                itemCount = response.summary.itemCount,
            ),
        )
    }

    /**
     * Fetches the user's Steam inventory directly from the device (each device has its
     * own IP, so this can't be affected by Steam rate-limiting our server's shared IP),
     * then sends the matched items to the backend. Returns the number of items synced.
     */
    suspend fun sync(): Int {
        val steamId = backendApi.getMe().steamId
            ?: throw IllegalStateException("No linked Steam account")

        val inventory = try {
            steamApiService.getPublicInventory("https://steamcommunity.com/inventory/$steamId/730/2")
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) throw SteamInventoryPrivateException()
            throw e
        }
        if (inventory.success != 1) {
            throw IllegalStateException("Steam inventory request did not succeed")
        }

        val descByKey = (inventory.descriptions ?: emptyList())
            .associateBy { "${it.classid}_${it.instanceid}" }
        val items = (inventory.assets ?: emptyList()).mapNotNull { asset ->
            val desc = descByKey["${asset.classid}_${asset.instanceid}"] ?: return@mapNotNull null
            if (desc.market_hash_name.isBlank()) return@mapNotNull null
            SyncItemRequest(assetId = asset.assetid, marketHashName = desc.market_hash_name)
        }

        return backendApi.syncPortfolio(SyncPortfolioRequest(items)).synced
    }

    suspend fun updateAcquirePrice(skinId: String, assetId: String, newPrice: Double) {
        backendApi.upsertPortfolioItem(UpsertPortfolioItemRequest(skinId, assetId, newPrice))
    }

    suspend fun deleteItem(id: String) {
        backendApi.deletePortfolioItem(id)
    }
}
