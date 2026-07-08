package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
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
    private val backendApi: CS2BackendApiService
) {

    fun getTrendingSkins(): Flow<List<Skin>> = flow {
        emit(backendApi.getTopMovers().map { it.toDomain() })
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
                rarity = rarity?.displayName,
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
            rarity = filters.rarity?.displayName,
            wear = filters.wear?.displayName,
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
        val history = getPriceHistory(id, range = "24h")
        return skin.toDomain(priceHistory = history)
    }

    suspend fun getPriceHistory(skinId: String, range: String): List<PricePoint> = runCatching {
        backendApi.getPriceHistory(skinId, range).map { it.toDomain() }
    }.getOrDefault(emptyList())

    suspend fun getAllWeapons(): List<String> = backendApi.getWeapons()
}
