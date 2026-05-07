package com.burixer85.cs2skinflip.core.domain.model

data class Skin(
    val id: String,
    val name: String,
    val weapon: String,
    val skinName: String,
    val rarity: SkinRarity,
    val wear: SkinWear,
    val isStatTrak: Boolean = false,
    val isSouvenir: Boolean = false,
    val imageUrl: String,
    val steamPrice: Double,
    val csFloatPrice: Double?,
    val skinportPrice: Double?,
    val dmarketPrice: Double?,
    val priceChange24h: Double,          // percentage
    val volume24h: Int,
    val floatMin: Float,
    val floatMax: Float,
    val floatMedian: Float,
    val priceHistory: List<PricePoint> = emptyList(),
    val collection: String? = null
) {
    val lowestMarketPrice: Double
        get() = listOfNotNull(steamPrice, csFloatPrice, skinportPrice, dmarketPrice).minOrNull() ?: steamPrice

    val displayName: String
        get() = buildString {
            if (isSouvenir) append("Souvenir ")
            append(name)
            if (isStatTrak) append(" (StatTrak™)")
        }
}

enum class SkinRarity(val displayName: String, val colorHex: String) {
    CONSUMER("Consumer Grade", "#B0C3D9"),
    INDUSTRIAL("Industrial Grade", "#5E98D9"),
    MIL_SPEC("Mil-Spec", "#4B69FF"),
    RESTRICTED("Restricted", "#8847FF"),
    CLASSIFIED("Classified", "#D32CE6"),
    COVERT("Covert", "#EB4B4B"),
    CONTRABAND("Contraband", "#E4AE39"),
    KNIFE("★ Special", "#E4AE39")
}

enum class SkinWear(val displayName: String, val abbrev: String, val floatRange: ClosedFloatingPointRange<Float>) {
    FACTORY_NEW("Factory New", "FN", 0.00f..0.07f),
    MINIMAL_WEAR("Minimal Wear", "MW", 0.07f..0.15f),
    FIELD_TESTED("Field-Tested", "FT", 0.15f..0.38f),
    WELL_WORN("Well-Worn", "WW", 0.38f..0.45f),
    BATTLE_SCARRED("Battle-Scarred", "BS", 0.45f..1.00f)
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
    STEAM("Steam"),
    CSFLOAT("CSFloat"),
    SKINPORT("Skinport"),
    DMARKET("DMarket")
}
