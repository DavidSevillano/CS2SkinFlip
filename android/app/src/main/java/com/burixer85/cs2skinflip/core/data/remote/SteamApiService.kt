package com.burixer85.cs2skinflip.core.data.remote

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface SteamApiService {

    // Perfil del jugador
    @GET("ISteamUser/GetPlayerSummaries/v0002/")
    suspend fun getPlayerSummaries(
        @Query("key") apiKey: String,
        @Query("steamids") steamId: String
    ): PlayerSummariesResponse

    /**
     * Public inventory endpoint (no API key, works from any IP) — fetched directly from
     * the device rather than the backend so Steam's per-IP rate limiting on this
     * undocumented endpoint can't be triggered by unrelated traffic sharing our
     * server's outbound IP. count is capped at 1000 — Steam returns 400 above that.
     */
    @GET
    @Headers("User-Agent: Mozilla/5.0")
    suspend fun getPublicInventory(
        @Url url: String,
        @Query("l") language: String = "english",
        @Query("count") count: Int = 1000,
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
    val success: Int?,
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
