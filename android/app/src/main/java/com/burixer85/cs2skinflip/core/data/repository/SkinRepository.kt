package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.toDomain
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinRepository @Inject constructor(
    private val backendApi: CS2BackendApiService
) {

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

    suspend fun getSkinById(id: String): Skin? {
        return runCatching {
            val skin = backendApi.getSkin(id)
            val history = runCatching {
                backendApi.getPriceHistory(id).map { it.toDomain() }
            }.getOrDefault(emptyList())
            skin.toDomain(priceHistory = history)
        }.getOrElse {
            MockData.getSkinById(id)
        }
    }

    suspend fun getAllWeapons(): List<String> = runCatching {
        backendApi.getWeapons()
    }.getOrElse {
        MockData.skins.map { it.weapon }.distinct().sorted()
    }
}
