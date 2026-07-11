package com.burixer85.cs2skinflip.shared.seo

import com.burixer85.cs2skinflip.shared.model.Skin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SITE = "https://cs2skinflip.pages.dev"

private fun skin(
    skinport: Double? = 12.5,
    csgoMarket: Double? = 9.99,
    waxpeer: Double? = 11.0,
    lowest: Double? = 9.99,
) = Skin(
    id = "ak-47-redline-field-tested",
    marketHashName = "AK-47 | Redline (Field-Tested)",
    weapon = "AK-47",
    rarity = "Classified",
    iconUrl = "https://cdn.example.com/ak-redline.png",
    skinportPrice = skinport,
    csgoMarketPrice = csgoMarket,
    waxpeerPrice = waxpeer,
    lowestPrice = lowest,
    priceChange24h = 3.2,
    updatedAt = "2026-07-01T00:00:00.000Z",
)

private fun unpriced() = skin(skinport = null, csgoMarket = null, waxpeer = null, lowest = null)

class CanonicalUrlTest {
    @Test
    fun buildsATrailingSlashUrl() {
        assertEquals(
            "$SITE/skin/ak-47-redline-field-tested/",
            canonicalUrl(SITE, "ak-47-redline-field-tested"),
        )
    }
}

class PageTitleTest {
    @Test
    fun containsMarketHashNameAndLivePrice() {
        assertEquals("AK-47 | Redline (Field-Tested) Price — $9.99 | CS2SkinFlip", pageTitle(skin()))
    }

    @Test
    fun fallsBackToNaWhenUnpriced() {
        assertEquals("AK-47 | Redline (Field-Tested) Price — N/A | CS2SkinFlip", pageTitle(unpriced()))
    }
}

class MetaDescriptionTest {
    @Test
    fun mentionsPriceAndPluralMarketplaceCount() {
        val description = metaDescription(skin())
        assertTrue(description.contains("$9.99"))
        assertTrue(description.contains("3 marketplaces"))
    }

    @Test
    fun singularisesForASingleMarketplace() {
        val description = metaDescription(skin(skinport = null, csgoMarket = null, waxpeer = 7.25, lowest = 7.25))
        assertTrue(description.contains("1 marketplace."))
    }

    @Test
    fun fallsBackWhenUnpriced() {
        assertEquals(
            "Live price tracking and price history for AK-47 | Redline (Field-Tested).",
            metaDescription(unpriced()),
        )
    }
}

class ProductJsonLdTest {
    @Test
    fun emitsAggregateOfferWithBareLowPriceAndOfferCount() {
        val jsonLd = productJsonLd(skin())

        assertEquals("https://schema.org", jsonLd["@context"]!!.jsonPrimitive.content)
        assertEquals("Product", jsonLd["@type"]!!.jsonPrimitive.content)
        assertEquals("AK-47 | Redline (Field-Tested)", jsonLd["name"]!!.jsonPrimitive.content)
        assertEquals("https://cdn.example.com/ak-redline.png", jsonLd["image"]!!.jsonPrimitive.content)
        assertEquals("AK-47", jsonLd["category"]!!.jsonPrimitive.content)

        val offers = jsonLd["offers"]!!.jsonObject
        assertEquals("AggregateOffer", offers["@type"]!!.jsonPrimitive.content)
        assertEquals("USD", offers["priceCurrency"]!!.jsonPrimitive.content)
        assertEquals("9.99", offers["lowPrice"]!!.jsonPrimitive.content)
        assertEquals(3, offers["offerCount"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun neverPrefixesLowPriceWithACurrencySymbol() {
        val offers = productJsonLd(skin())["offers"]!!.jsonObject
        assertFalse(offers["lowPrice"]!!.jsonPrimitive.content.contains("$"))
    }

    @Test
    fun reportsOfferCountOneForASingleMarketplace() {
        val offers = productJsonLd(
            skin(skinport = null, csgoMarket = null, waxpeer = 7.25, lowest = 7.25),
        )["offers"]!!.jsonObject

        assertEquals(1, offers["offerCount"]!!.jsonPrimitive.content.toInt())
        assertEquals("7.25", offers["lowPrice"]!!.jsonPrimitive.content)
    }

    @Test
    fun omitsOffersEntirelyWhenNoMarketplaceQuoted() {
        val jsonLd: JsonObject = productJsonLd(unpriced())
        assertFalse(jsonLd.containsKey("offers"))
    }

    @Test
    fun escapesQuotesInTheSkinNameBySerialisation() {
        val dangerous = skin().copy(marketHashName = """AK-47 "quoted" | Redline""")
        val serialised = productJsonLd(dangerous).toString()
        assertTrue(serialised.contains("""\"quoted\""""))
    }
}

class BreadcrumbJsonLdTest {
    @Test
    fun emitsThreeLevelsEndingOnTheSkin() {
        val items = breadcrumbJsonLd(SITE, skin())["itemListElement"] as JsonArray

        assertEquals(3, items.size)
        val last = items[2].jsonObject
        assertEquals(3, last["position"]!!.jsonPrimitive.content.toInt())
        assertEquals("AK-47 | Redline (Field-Tested)", last["name"]!!.jsonPrimitive.content)
        assertEquals("$SITE/skin/ak-47-redline-field-tested/", last["item"]!!.jsonPrimitive.content)
    }

    @Test
    fun firstLevelIsHome() {
        val items = breadcrumbJsonLd(SITE, skin())["itemListElement"] as JsonArray
        val first = items[0].jsonObject
        assertEquals("Home", first["name"]!!.jsonPrimitive.content)
        assertEquals("$SITE/", first["item"]!!.jsonPrimitive.content)
    }
}
