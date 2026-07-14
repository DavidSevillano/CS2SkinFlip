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

    /**
     * Steam Community Market has no bulk pricing endpoint, and this one is
     * rate-limited hard enough that our backend's shared cloud IP gets a
     * permanent 429 from it. Called directly from the device instead — same
     * rationale as [getPublicInventory] above.
     */
    @GET
    @Headers("User-Agent: Mozilla/5.0")
    suspend fun getMarketPriceOverview(
        @Url url: String = "https://steamcommunity.com/market/priceoverview/",
        @Query("appid") appId: Int = 730,
        @Query("currency") currency: Int = 1, // USD
        @Query("market_hash_name") marketHashName: String,
    ): MarketPriceOverviewResponse
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

data class MarketPriceOverviewResponse(
    val success: Boolean,
    val lowest_price: String?,   // e.g. "$32.39" or "$1,234.56" (currency=1 → USD)
    val median_price: String?,
    val volume: String?,
)
