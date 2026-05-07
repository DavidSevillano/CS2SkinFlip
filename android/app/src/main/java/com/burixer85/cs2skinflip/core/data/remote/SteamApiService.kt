package com.burixer85.cs2skinflip.core.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface SteamApiService {

    // Perfil del jugador
    @GET("ISteamUser/GetPlayerSummaries/v0002/")
    suspend fun getPlayerSummaries(
        @Query("key") apiKey: String,
        @Query("steamids") steamId: String
    ): PlayerSummariesResponse

    // Inventario CS2 (appid 730)
    @GET("IEconService/GetInventoryItemsWithDescriptions/v1/")
    suspend fun getInventory(
        @Query("key") apiKey: String,
        @Query("steamid") steamId: String,
        @Query("appid") appId: Int = 730,
        @Query("contextid") contextId: Int = 2,
        @Query("get_descriptions") getDescriptions: Boolean = true
    ): InventoryResponse
}

// --- DTOs ---

data class PlayerSummariesResponse(val response: PlayerSummariesData)
data class PlayerSummariesData(val players: List<PlayerDto>)
data class PlayerDto(
    val steamid: String,
    val personaname: String,
    val avatarfull: String
)

data class InventoryResponse(
    val assets: List<AssetDto>?,
    val descriptions: List<DescriptionDto>?,
    val total_inventory_count: Int?
)

data class AssetDto(
    val assetid: String,
    val classid: String,
    val instanceid: String,
    val amount: String
)

data class DescriptionDto(
    val classid: String,
    val instanceid: String,
    val market_hash_name: String,
    val name: String,
    val icon_url: String,
    val tags: List<TagDto>?
)

data class TagDto(
    val category: String,
    val internal_name: String,
    val localized_tag_name: String
)
