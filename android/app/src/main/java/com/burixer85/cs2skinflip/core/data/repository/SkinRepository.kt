package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinRepository @Inject constructor() {

    fun getTrendingSkins(): Flow<List<Skin>> = flow {
        delay(800)  // simulate network
        emit(MockData.trendingSkins)
    }

    fun searchSkins(
        query: String,
        weapon: String? = null,
        rarity: SkinRarity? = null,
        wear: SkinWear? = null,
        statTrakOnly: Boolean = false
    ): Flow<List<Skin>> = flow {
        delay(300)
        var results = MockData.searchSkins(query)
        if (weapon != null) results = results.filter { it.weapon == weapon }
        if (rarity != null) results = results.filter { it.rarity == rarity }
        if (wear != null) results = results.filter { it.wear == wear }
        if (statTrakOnly) results = results.filter { it.isStatTrak }
        emit(results)
    }

    suspend fun getSkinById(id: String): Skin? {
        delay(200)
        return MockData.getSkinById(id)
    }

    fun getAllWeapons(): List<String> = MockData.skins.map { it.weapon }.distinct().sorted()
}
