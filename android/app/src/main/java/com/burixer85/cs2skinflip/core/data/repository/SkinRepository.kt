package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.SteamApiService
import com.burixer85.cs2skinflip.core.data.remote.toDomain
import com.burixer85.cs2skinflip.core.domain.model.PricePoint
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import com.burixer85.cs2skinflip.features.search.SearchFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend prices are kept fresh by a 2-hourly bulk job that aggregates 3
 * marketplaces (Skinport, CS:GO Market, Waxpeer). All endpoints return final
 * prices directly — no live calls, no client-side enrichment.
 */
@Singleton
class SkinRepository @Inject constructor(
    private val backendApi: CS2BackendApiService,
    private val steamApi: SteamApiService,
) {

    fun getTrendingSkins(direction: String = "rising"): Flow<List<Skin>> = flow {
        emit(backendApi.getTopMovers(direction).map { it.toDomain() })
    }

    fun searchSkins(
        query: String,
        weapon: String? = null,
        rarity: SkinRarity? = null,
        wear: SkinWear? = null,
        statTrakOnly: Boolean = false
    ): Flow<List<Skin>> = flow {
        val hasFilters = query.isNotBlank() || weapon != null || rarity != null || wear != null || statTrakOnly
        val remote = runCatching {
            backendApi.searchSkins(
                query = query.ifBlank { null },
                weapon = weapon,
                rarity = rarity?.apiValue,
                sort = if (hasFilters) "name" else "random"
            ).data.map { it.toDomain() }
        }.getOrElse {
            var results = MockData.searchSkins(query)
            if (weapon != null) results = results.filter { it.weapon == weapon }
            if (rarity != null) results = results.filter { it.rarity == rarity }
            results
        }

        val filtered = remote
            .let { list -> if (wear != null) list.filter { it.wear == wear } else list }
            .let { list -> if (statTrakOnly) list.filter { it.isStatTrak } else list }

        emit(filtered)
    }

    /** Paginated search — returns (skins, hasMore, total) */
    suspend fun searchSkinsPage(
        query: String,
        filters: SearchFilters,
        page: Int,
        limit: Int
    ): Triple<List<Skin>, Boolean, Int> {
        val response = backendApi.searchSkins(
            query = query.ifBlank { null },
            weapon = filters.weapon,
            rarity = filters.rarity?.apiValue,
            wear = filters.wear?.apiValue,
            statTrak = if (filters.statTrakOnly) true else null,
            minPrice = filters.minPrice,
            maxPrice = filters.maxPrice,
            page = page,
            limit = limit,
            sort = filters.sortBy.apiValue,
        )
        val skins = response.data.map { it.toDomain() }
        val total = response.pagination.total
        val hasMore = page < response.pagination.pages
        return Triple(skins, hasMore, total)
    }

    suspend fun getSkinById(id: String): Skin {
        val skin = backendApi.getSkin(id)
        val history = getPriceHistory(id, range = "7d")
        return skin.toDomain(priceHistory = history)
    }

    suspend fun getPriceHistory(skinId: String, range: String): List<PricePoint> = runCatching {
        backendApi.getPriceHistory(skinId, range).map { it.toDomain() }
    }.getOrDefault(emptyList())

    suspend fun getAllWeapons(): List<String> = backendApi.getWeapons()

    /**
     * Live, on-demand call made directly from the device rather than through
     * the backend — Steam has no bulk catalog endpoint, and our backend's
     * shared cloud IP gets a permanent 429 from Steam on this endpoint (each
     * user's own IP does not). Any failure (network error, Steam rate-limit,
     * item not listed) collapses to null, which the UI renders identically
     * to "not listed".
     */
    suspend fun getSteamPrice(marketHashName: String): Double? = runCatching {
        val response = steamApi.getMarketPriceOverview(marketHashName = marketHashName)
        if (response.success) parseSteamPrice(response.lowest_price) else null
    }.getOrNull()
}

private fun parseSteamPrice(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    val value = cleaned.toDoubleOrNull()
    return if (value != null && value > 0) value else null
}
