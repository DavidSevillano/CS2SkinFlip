package com.burixer85.cs2skinflip.core.data.remote

import com.burixer85.cs2skinflip.core.domain.model.PricePoint
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale

// ─── Retrofit interface ───────────────────────────────────────────────────────

interface CS2BackendApiService {

    @GET("skins/top-movers")
    suspend fun getTopMovers(): List<TopMoverDto>

    @GET("skins/weapons")
    suspend fun getWeapons(): List<String>

    @GET("skins")
    suspend fun searchSkins(
        @Query("q") query: String? = null,
        @Query("weapon") weapon: String? = null,
        @Query("rarity") rarity: String? = null,
        @Query("wear") wear: String? = null,
        @Query("statTrak") statTrak: Boolean? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("sort") sort: String = "random"
    ): SkinsPageDto

    @GET("skins/{id}")
    suspend fun getSkin(@Path("id") id: String): BackendSkinDto

    @GET("skins/{id}/price-history")
    suspend fun getPriceHistory(
        @Path("id") id: String,
        @Query("range") range: String = "24h"
    ): List<PriceHistoryDto>

    @GET("auth/me")
    suspend fun getMe(): MeResponseDto

    @GET("alerts")
    suspend fun getAlerts(): AlertsResponseDto

    @POST("alerts")
    suspend fun createAlert(@Body body: CreateAlertRequest): AlertDto

    @PUT("alerts/{id}")
    suspend fun updateAlert(@Path("id") id: String, @Body body: UpdateAlertRequest): AlertDto

    @DELETE("alerts/{id}")
    suspend fun deleteAlert(@Path("id") id: String)

    @GET("watchlist")
    suspend fun getWatchlist(): List<WatchlistItemDto>

    @PUT("auth/me/fcm-token")
    suspend fun updateFcmToken(@Body body: FcmTokenRequest)

    /**
     * Batch price refresh — returns DB-cached prices for up to 50 skins in one call.
     * Use this instead of calling [getSkin] 20 times after a list loads.
     */
    @GET("prices/batch")
    suspend fun getPricesBatch(
        @Query("ids") ids: String,   // comma-separated skin IDs, max 50
    ): Map<String, BatchSkinPriceDto>
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class TopMoverDto(
    val skinId: String,
    val marketHashName: String,
    val name: String,
    val iconUrl: String,
    val skinportPrice: Double?,
    val csgoMarketPrice: Double?,
    val waxpeerPrice: Double?,
    val lowestPrice: Double?,
    val priceChange24h: Double?,
    val volume24h: Int?,
    val updatedAt: String
)

data class SkinsPageDto(
    val data: List<BackendSkinDto>,
    val pagination: PaginationDto
)

data class PaginationDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int
)

data class BackendSkinDto(
    val id: String,
    val name: String,
    val marketHashName: String,
    val weapon: String,
    val rarity: String,
    val iconUrl: String,
    val price: BackendSkinPriceDto?,
)

data class BackendSkinPriceDto(
    val skinportPrice: Double?,       // USD — Skinport
    val csgoMarketPrice: Double?,     // USD — CS:GO Market
    val waxpeerPrice: Double?,        // USD — Waxpeer
    val lowestPrice: Double?,
    val volume24h: Int?,
    val priceChange24h: Double?,
)

/** Lightweight price-only payload returned by GET /prices/batch. */
data class BatchSkinPriceDto(
    val skinportPrice: Double?,
    val csgoMarketPrice: Double?,
    val waxpeerPrice: Double?,
    val lowestPrice: Double?,
)

data class PriceHistoryDto(
    val price: Double,
    val timestamp: String,
    val source: String
)

data class AlertsResponseDto(
    val alerts: List<AlertDto>,
    val meta: AlertMetaDto
)

data class AlertDto(
    val id: String,
    val skinId: String,
    val skinName: String?,
    val skinImageUrl: String?,
    val type: String,
    val targetPrice: Double,
    val currentPrice: Double?,
    val isActive: Boolean,
    val isTriggered: Boolean,
    val createdAt: String
)

data class AlertMetaDto(
    val activeCount: Int,
    val limit: Int?,
    val isPremium: Boolean
)

data class WatchlistItemDto(
    val id: String,
    val skinId: String,
    val targetBuyPrice: Double?,
    val targetSellPrice: Double?,
    val alertEnabled: Boolean
)

data class CreateAlertRequest(
    val skinId: String,
    val type: String,
    val targetPrice: Double,
)

data class UpdateAlertRequest(
    val targetPrice: Double? = null,
    val isActive: Boolean? = null,
    val type: String? = null,
)

// ─── Mappers ──────────────────────────────────────────────────────────────────

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

fun TopMoverDto.toDomain(): Skin {
    val wear = parseWearFromName(marketHashName)
    val cleanName = marketHashName.substringBefore("(").trim()
    val weaponName = cleanName.substringBefore("|").trim()
    val patternName = cleanName.substringAfter("|", cleanName).trim()
    return Skin(
        id = skinId,
        name = cleanName,
        marketHashName = marketHashName,
        weapon = weaponName,
        skinName = patternName,
        rarity = parseRarityFromName(marketHashName),
        wear = wear,
        isStatTrak = marketHashName.contains("StatTrak™"),
        isSouvenir = marketHashName.contains("Souvenir"),
        imageUrl = iconUrl,
        skinportPrice = skinportPrice,
        csgoMarketPrice = csgoMarketPrice,
        waxpeerPrice = waxpeerPrice,
        lowestPrice = lowestPrice,
        priceChange24h = priceChange24h,
        volume24h = volume24h ?: 0,
        floatMin = wear.floatRange.start,
        floatMax = wear.floatRange.endInclusive,
        floatMedian = (wear.floatRange.start + wear.floatRange.endInclusive) / 2f
    )
}

fun BackendSkinDto.toDomain(
    priceChange24h: Double? = null,
    priceHistory: List<PricePoint> = emptyList()
): Skin {
    val wear = parseWearFromName(marketHashName)
    val cleanName = marketHashName.substringBefore("(").trim()
    val weaponName = cleanName.substringBefore("|").trim()
    val patternName = cleanName.substringAfter("|", cleanName).trim()
    return Skin(
        id = id,
        name = cleanName,
        marketHashName = marketHashName,
        weapon = weaponName,
        skinName = patternName,
        rarity = parseRarity(rarity),
        wear = wear,
        isStatTrak = marketHashName.contains("StatTrak™"),
        isSouvenir = marketHashName.contains("Souvenir"),
        imageUrl = iconUrl,
        skinportPrice = price?.skinportPrice,
        csgoMarketPrice = price?.csgoMarketPrice,
        waxpeerPrice = price?.waxpeerPrice,
        lowestPrice = price?.lowestPrice,
        // priceChange24h from the API takes priority; caller can override if needed
        priceChange24h = price?.priceChange24h ?: priceChange24h,
        volume24h = price?.volume24h ?: 0,
        floatMin = wear.floatRange.start,
        floatMax = wear.floatRange.endInclusive,
        floatMedian = (wear.floatRange.start + wear.floatRange.endInclusive) / 2f,
        priceHistory = priceHistory,
    )
}

fun PriceHistoryDto.toDomain(): PricePoint {
    val ts = runCatching { isoFormat.parse(timestamp)?.time }.getOrNull() ?: System.currentTimeMillis()
    return PricePoint(timestamp = ts, price = price)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun parseWearFromName(name: String): SkinWear = when {
    name.contains("Factory New",    ignoreCase = true) -> SkinWear.FACTORY_NEW
    name.contains("Minimal Wear",   ignoreCase = true) -> SkinWear.MINIMAL_WEAR
    name.contains("Field-Tested",   ignoreCase = true) -> SkinWear.FIELD_TESTED
    name.contains("Well-Worn",      ignoreCase = true) -> SkinWear.WELL_WORN
    name.contains("Battle-Scarred", ignoreCase = true) -> SkinWear.BATTLE_SCARRED
    else -> SkinWear.FIELD_TESTED
}

fun parseRarity(raw: String): SkinRarity = when {
    raw.contains("Consumer",    ignoreCase = true) -> SkinRarity.CONSUMER
    raw.contains("Industrial",  ignoreCase = true) -> SkinRarity.INDUSTRIAL
    raw.contains("Mil-Spec",    ignoreCase = true) -> SkinRarity.MIL_SPEC
    raw.contains("Restricted",  ignoreCase = true) -> SkinRarity.RESTRICTED
    raw.contains("Classified",  ignoreCase = true) -> SkinRarity.CLASSIFIED
    raw.contains("Contraband",  ignoreCase = true) -> SkinRarity.CONTRABAND
    raw.contains("Covert",      ignoreCase = true) -> SkinRarity.COVERT
    else -> SkinRarity.MIL_SPEC
}

fun parseRarityFromName(marketHashName: String): SkinRarity = when {
    marketHashName.startsWith("★") -> SkinRarity.KNIFE
    else -> SkinRarity.COVERT // default for top movers (no rarity in that endpoint)
}

// ─── Auth DTOs ───────────────────────────────────────────────────────────────

data class FcmTokenRequest(
    val token: String,
)

data class MeResponseDto(
    val id: String,
    val steamId: String?,
    val username: String,
    val avatarUrl: String?,
    val isPremium: Boolean,
    val premiumUntil: String?,
)
