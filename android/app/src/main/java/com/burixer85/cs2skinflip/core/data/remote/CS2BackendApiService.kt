package com.burixer85.cs2skinflip.core.data.remote

import com.burixer85.cs2skinflip.core.domain.model.PricePoint
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import retrofit2.http.GET
import retrofit2.http.Header
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
        @Query("days") days: Int = 30
    ): List<PriceHistoryDto>

    @GET("alerts")
    suspend fun getAlerts(@Header("Authorization") bearer: String): AlertsResponseDto

    @GET("watchlist")
    suspend fun getWatchlist(@Header("Authorization") bearer: String): List<WatchlistItemDto>
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class TopMoverDto(
    val skinId: String,
    val marketHashName: String,
    val name: String,
    val iconUrl: String,
    val steamPrice: Double?,
    val csFloatPrice: Double?,
    val skinportPrice: Double?,
    val dmarketPrice: Double?,
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
    val price: BackendSkinPriceDto?
)

data class BackendSkinPriceDto(
    val steamPrice: Double?,
    val csFloatPrice: Double?,
    val skinportPrice: Double?,
    val dmarketPrice: Double?,
    val lowestPrice: Double?,
    val volume24h: Int?
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
    val type: String,
    val targetPrice: Double,
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

// ─── Mappers ──────────────────────────────────────────────────────────────────

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

fun TopMoverDto.toDomain(): Skin {
    val wear = parseWearFromName(marketHashName)
    // "AK-47 | Redline (Field-Tested)" → name="AK-47 | Redline", weapon="AK-47", skinName="Redline"
    val cleanName = marketHashName.substringBefore("(").trim()
    val weaponName = cleanName.substringBefore("|").trim()
    val patternName = cleanName.substringAfter("|", cleanName).trim()
    return Skin(
        id = skinId,
        name = cleanName,
        weapon = weaponName,
        skinName = patternName,
        rarity = parseRarityFromName(marketHashName),
        wear = wear,
        isStatTrak = marketHashName.contains("StatTrak™"),
        isSouvenir = marketHashName.contains("Souvenir"),
        imageUrl = iconUrl,
        steamPrice = lowestPrice ?: steamPrice ?: 0.0,
        csFloatPrice = csFloatPrice,
        skinportPrice = skinportPrice,
        dmarketPrice = dmarketPrice,
        priceChange24h = priceChange24h ?: 0.0,
        volume24h = volume24h ?: 0,
        floatMin = wear.floatRange.start,
        floatMax = wear.floatRange.endInclusive,
        floatMedian = (wear.floatRange.start + wear.floatRange.endInclusive) / 2f
    )
}

fun BackendSkinDto.toDomain(
    priceChange24h: Double = 0.0,
    priceHistory: List<PricePoint> = emptyList()
): Skin {
    val wear = parseWearFromName(marketHashName)
    val cleanName = marketHashName.substringBefore("(").trim()
    val weaponName = cleanName.substringBefore("|").trim()
    val patternName = cleanName.substringAfter("|", cleanName).trim()
    return Skin(
        id = id,
        name = cleanName,
        weapon = weaponName,
        skinName = patternName,
        rarity = parseRarity(rarity),
        wear = wear,
        isStatTrak = marketHashName.contains("StatTrak™"),
        isSouvenir = marketHashName.contains("Souvenir"),
        imageUrl = iconUrl,
        steamPrice = price?.lowestPrice ?: price?.steamPrice ?: 0.0,
        csFloatPrice = price?.csFloatPrice,
        skinportPrice = price?.skinportPrice,
        dmarketPrice = price?.dmarketPrice,
        priceChange24h = priceChange24h,
        volume24h = price?.volume24h ?: 0,
        floatMin = wear.floatRange.start,
        floatMax = wear.floatRange.endInclusive,
        floatMedian = (wear.floatRange.start + wear.floatRange.endInclusive) / 2f,
        priceHistory = priceHistory
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
