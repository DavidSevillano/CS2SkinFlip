package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.toDomain
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import com.burixer85.cs2skinflip.features.search.SearchFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinRepository @Inject constructor(
    private val backendApi: CS2BackendApiService
) {

    /**
     * Cache of live prices fetched by the detail screen (keyed by skinId).
     * The detail endpoint returns real-time DMarket exchange prices, which are
     * more accurate than the bulk-job aggregator prices stored in the DB.
     * Search and Home screens observe this to keep their cards in sync.
     */
    private val _livePriceCache = MutableStateFlow<Map<String, Skin>>(emptyMap())
    val livePriceCache: StateFlow<Map<String, Skin>> = _livePriceCache

    fun getTrendingSkins(): Flow<List<Skin>> = flow {
        val skins = runCatching { backendApi.getTopMovers().map { it.toDomain() } }
            .getOrElse { MockData.trendingSkins }
        emit(skins)
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

    suspend fun getSkinById(id: String): Skin? {
        return runCatching {
            val skin = backendApi.getSkin(id)
            val history = runCatching {
                backendApi.getPriceHistory(id).map { it.toDomain() }
            }.getOrDefault(emptyList())
            val domainSkin = skin.toDomain(priceHistory = history)
            // Propagate live prices back to search/home so cards show the correct lowestPrice
            _livePriceCache.value = _livePriceCache.value + (id to domainSkin)
            domainSkin
        }.getOrElse {
            MockData.getSkinById(id)
        }
    }

    /**
     * Batch live price refresh — returns the same list with live prices applied.
     *
     * Calls `GET /prices/batch?ids=...`. The backend serves Skinport from its in-memory
     * map (zero outbound API calls) and DMarket from the live exchange API with a 5-min
     * Redis cache. Returns the patched list so callers can show correct prices immediately
     * (no double-render flicker). Also writes results into [livePriceCache] so that
     * detail-screen price updates still propagate back to list cards.
     *
     * On failure returns the original list unchanged — cards show whatever the DB had.
     */
    suspend fun refreshSkinPricesBatch(skins: List<Skin>): List<Skin> {
        if (skins.isEmpty()) return skins
        return runCatching {
            val patches = backendApi.getPricesBatch(skins.joinToString(",") { it.id })
            val updated = skins.map { skin ->
                patches[skin.id]?.let { p ->
                    skin.copy(
                        cs2capPrice     = p.cs2capPrice,
                        csfloatPrice    = p.csfloatPrice,
                        csgoMarketPrice = p.csgoMarketPrice,
                        lowestPrice     = p.lowestPrice,
                    )
                } ?: skin
            }
            // Also write into cache so detail→list price propagation still works
            _livePriceCache.value = _livePriceCache.value + updated.associateBy { it.id }
            updated
        }.getOrElse { skins }
    }

    suspend fun getAllWeapons(): List<String> = runCatching {
        backendApi.getWeapons()
    }.getOrElse {
        MockData.skins.map { it.weapon }.distinct().sorted()
    }
}
