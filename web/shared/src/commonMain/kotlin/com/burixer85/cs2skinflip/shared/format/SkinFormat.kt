package com.burixer85.cs2skinflip.shared.format

import com.burixer85.cs2skinflip.shared.model.Skin
import kotlin.math.abs
import kotlin.math.round

private val WEARS = setOf(
    "Factory New",
    "Minimal Wear",
    "Field-Tested",
    "Well-Worn",
    "Battle-Scarred",
)

private val TRAILING_PARENTHETICAL = Regex("""\(([^)]+)\)\s*$""")

/** Wear lives only in the trailing "(…)" of marketHashName — the backend has no wear column. */
fun parseWear(marketHashName: String): String? {
    val candidate = TRAILING_PARENTHETICAL.find(marketHashName)?.groupValues?.get(1) ?: return null
    return candidate.takeIf { it in WEARS }
}

fun isStatTrak(marketHashName: String): Boolean = marketHashName.contains("StatTrak")

data class MarketplaceEntry(val name: String, val price: Double)

/** Cheapest-first list of the marketplaces that actually quoted a price. */
fun marketplaceEntries(skin: Skin): List<MarketplaceEntry> = listOf(
    MarketplaceEntry("Skinport", skin.skinportPrice ?: 0.0),
    MarketplaceEntry("CS:GO Market", skin.csgoMarketPrice ?: 0.0),
    MarketplaceEntry("Waxpeer", skin.waxpeerPrice ?: 0.0),
).filter { it.price > 0.0 }.sortedBy { it.price }

fun formatPrice(value: Double?): String = if (value == null) "N/A" else "$" + decimalString(value)

fun formatChange(value: Double?): String? {
    if (value == null) return null
    val prefix = if (value > 0.0) "+" else ""
    return "$prefix${decimalString(value)}%"
}

/**
 * `String.format` is JVM-only, so decimals are assembled by hand to keep this in commonMain.
 * Rounding once on the total cent count also handles the 0.999 -> "1.00" carry.
 */
fun decimalString(value: Double): String {
    val negative = value < 0.0
    val totalCents = round(abs(value) * 100.0).toLong()
    val whole = totalCents / 100
    val cents = totalCents % 100
    val sign = if (negative && totalCents > 0L) "-" else ""
    return "$sign$whole.${cents.toString().padStart(2, '0')}"
}
