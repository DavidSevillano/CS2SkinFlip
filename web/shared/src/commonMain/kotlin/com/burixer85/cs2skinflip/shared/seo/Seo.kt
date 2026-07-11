package com.burixer85.cs2skinflip.shared.seo

import com.burixer85.cs2skinflip.shared.format.decimalString
import com.burixer85.cs2skinflip.shared.format.formatPrice
import com.burixer85.cs2skinflip.shared.format.marketplaceEntries
import com.burixer85.cs2skinflip.shared.model.Skin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

const val SITE_NAME = "CS2SkinFlip"

fun canonicalUrl(siteUrl: String, skinId: String): String = "$siteUrl/skin/$skinId/"

fun pageTitle(skin: Skin): String =
    "${skin.marketHashName} Price — ${formatPrice(skin.lowestPrice)} | $SITE_NAME"

fun metaDescription(skin: Skin): String {
    val markets = marketplaceEntries(skin)
    val lowest = skin.lowestPrice
    if (lowest == null || markets.isEmpty()) {
        return "Live price tracking and price history for ${skin.marketHashName}."
    }
    val plural = if (markets.size == 1) "marketplace" else "marketplaces"
    return "The current lowest price for ${skin.marketHashName} is ${formatPrice(lowest)}, " +
        "compared across ${markets.size} $plural."
}

/** Product + AggregateOffer — this is what earns the price rich snippet in Google. */
fun productJsonLd(skin: Skin): JsonObject {
    val markets = marketplaceEntries(skin)
    val lowest = skin.lowestPrice

    return buildJsonObject {
        put("@context", "https://schema.org")
        put("@type", "Product")
        put("name", skin.marketHashName)
        put("image", skin.iconUrl)
        put("category", skin.weapon)
        if (markets.isNotEmpty() && lowest != null) {
            putJsonObject("offers") {
                put("@type", "AggregateOffer")
                put("priceCurrency", "USD")
                // schema.org wants a bare number string, never a currency symbol.
                put("lowPrice", decimalString(lowest))
                put("offerCount", markets.size)
            }
        }
    }
}

fun breadcrumbJsonLd(siteUrl: String, skin: Skin): JsonObject = buildJsonObject {
    put("@context", "https://schema.org")
    put("@type", "BreadcrumbList")
    put(
        "itemListElement",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("@type", "ListItem")
                    put("position", 1)
                    put("name", "Home")
                    put("item", "$siteUrl/")
                },
            )
            // No per-weapon page exists in v1, so this level points at the home page
            // rather than a URL that would 404.
            add(
                buildJsonObject {
                    put("@type", "ListItem")
                    put("position", 2)
                    put("name", skin.weapon)
                    put("item", "$siteUrl/")
                },
            )
            add(
                buildJsonObject {
                    put("@type", "ListItem")
                    put("position", 3)
                    put("name", skin.marketHashName)
                    put("item", canonicalUrl(siteUrl, skin.id))
                },
            )
        },
    )
}
