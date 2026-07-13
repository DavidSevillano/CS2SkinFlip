package com.burixer85.cs2skinflip.core.domain.model

import androidx.annotation.StringRes
import com.burixer85.cs2skinflip.R

data class Skin(
    val id: String,
    val name: String,
    val marketHashName: String = name,
    val weapon: String,
    val skinName: String,
    val rarity: SkinRarity,
    val wear: SkinWear,
    val isStatTrak: Boolean = false,
    val isSouvenir: Boolean = false,
    val imageUrl: String,
    val skinportPrice: Double?,     // USD — Skinport
    val csgoMarketPrice: Double?,   // USD — CS:GO Market
    val waxpeerPrice: Double?,      // USD — Waxpeer
    val lowestPrice: Double? = null,  // pre-computed lowest across all markets (from backend)
    val priceChange24h: Double?,      // percentage, null if no history yet
    val volume24h: Int,
    val floatMin: Float,
    val floatMax: Float,
    val floatMedian: Float,
    val priceHistory: List<PricePoint> = emptyList(),
    val collection: String? = null,
) {
    /** Uses backend's pre-computed lowest price; falls back to min of individual sources. */
    val lowestMarketPrice: Double
        get() = lowestPrice
            ?: listOfNotNull(skinportPrice, csgoMarketPrice, waxpeerPrice).minOrNull()
            ?: 0.0

    val displayName: String
        get() = buildString {
            if (isSouvenir) append("Souvenir ")
            append(name)
            if (isStatTrak) append(" (StatTrak™)")
        }
}

/**
 * [apiValue] is the raw English name the backend expects for search/filter query params
 * (see `CLAUDE.md` — filtering matches `marketHashName` against this literal text), so it
 * must stay untranslated. [displayNameRes] is the localized label shown in the UI.
 */
enum class SkinRarity(@StringRes val displayNameRes: Int, val apiValue: String, val colorHex: String) {
    CONSUMER(R.string.rarity_consumer, "Consumer Grade", "#B0C3D9"),
    INDUSTRIAL(R.string.rarity_industrial, "Industrial Grade", "#5E98D9"),
    MIL_SPEC(R.string.rarity_mil_spec, "Mil-Spec", "#4B69FF"),
    RESTRICTED(R.string.rarity_restricted, "Restricted", "#8847FF"),
    CLASSIFIED(R.string.rarity_classified, "Classified", "#D32CE6"),
    COVERT(R.string.rarity_covert, "Covert", "#EB4B4B"),
    CONTRABAND(R.string.rarity_contraband, "Contraband", "#E4AE39"),
    KNIFE(R.string.rarity_knife, "★ Special", "#E4AE39")
}

/** [apiValue]: see [SkinRarity] doc — same backend-contract reasoning applies here. */
enum class SkinWear(@StringRes val displayNameRes: Int, val abbrev: String, val apiValue: String, val floatRange: ClosedFloatingPointRange<Float>) {
    FACTORY_NEW(R.string.wear_factory_new, "FN", "Factory New", 0.00f..0.07f),
    MINIMAL_WEAR(R.string.wear_minimal_wear, "MW", "Minimal Wear", 0.07f..0.15f),
    FIELD_TESTED(R.string.wear_field_tested, "FT", "Field-Tested", 0.15f..0.38f),
    WELL_WORN(R.string.wear_well_worn, "WW", "Well-Worn", 0.38f..0.45f),
    BATTLE_SCARRED(R.string.wear_battle_scarred, "BS", "Battle-Scarred", 0.45f..1.00f)
}

data class PricePoint(
    val timestamp: Long,
    val price: Double
)

data class MarketPrice(
    val marketplace: Marketplace,
    val price: Double,
    val listingCount: Int = 0,
    val url: String? = null
)

enum class Marketplace(val displayName: String) {
    SKINPORT("Skinport"),
    CSGO_MARKET("CS:GO Market"),
    WAXPEER("Waxpeer"),
    STEAM("Steam Community Market"),
}
