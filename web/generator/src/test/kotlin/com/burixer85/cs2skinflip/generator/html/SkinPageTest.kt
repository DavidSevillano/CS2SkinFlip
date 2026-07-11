package com.burixer85.cs2skinflip.generator.html

import com.burixer85.cs2skinflip.shared.model.PriceHistoryPoint
import com.burixer85.cs2skinflip.shared.model.Skin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SITE = "https://cs2skinflip.pages.dev"

private fun skin() = Skin(
    id = "ak-47-redline-field-tested",
    marketHashName = "AK-47 | Redline (Field-Tested)",
    weapon = "AK-47",
    rarity = "Classified",
    iconUrl = "https://cdn.example.com/ak-redline.png",
    skinportPrice = 12.5,
    csgoMarketPrice = 9.99,
    waxpeerPrice = 11.0,
    lowestPrice = 9.99,
    priceChange24h = 3.2,
    updatedAt = "2026-07-01T00:00:00.000Z",
)

class SkinPageTest {
    @Test
    fun startsWithADoctype() {
        assertTrue(renderSkinPage(SITE, skin(), emptyList(), emptyList()).startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun containsTheH1WithMarketHashName() {
        assertContains(renderSkinPage(SITE, skin(), emptyList(), emptyList()), "AK-47 | Redline (Field-Tested) Price")
    }

    @Test
    fun containsACanonicalLink() {
        val html = renderSkinPage(SITE, skin(), emptyList(), emptyList())
        assertContains(html, "rel=\"canonical\"")
        assertContains(html, "$SITE/skin/ak-47-redline-field-tested/")
    }

    @Test
    fun rendersQuotingMarketplacesCheapestFirst() {
        val html = renderSkinPage(SITE, skin(), emptyList(), emptyList())

        val csgoIndex = html.indexOf("CS:GO Market")
        val waxpeerIndex = html.indexOf("Waxpeer")
        val skinportIndex = html.indexOf("Skinport")

        assertTrue(csgoIndex > 0, "CS:GO Market row missing")
        assertTrue(csgoIndex < waxpeerIndex, "CS:GO Market (9.99) must precede Waxpeer (11.00)")
        assertTrue(waxpeerIndex < skinportIndex, "Waxpeer (11.00) must precede Skinport (12.50)")
        assertContains(html, "$9.99")
        assertContains(html, "$12.50")
    }

    @Test
    fun embedsProductAndBreadcrumbJsonLd() {
        val html = renderSkinPage(SITE, skin(), emptyList(), emptyList())

        assertContains(html, "application/ld+json")
        assertContains(html, "\"@type\":\"Product\"")
        assertContains(html, "\"@type\":\"AggregateOffer\"")
        assertContains(html, "\"lowPrice\":\"9.99\"")
        assertContains(html, "\"@type\":\"BreadcrumbList\"")
    }

    @Test
    fun omitsTheHistorySectionWhenThereIsNoHistory() {
        assertFalse(renderSkinPage(SITE, skin(), emptyList(), emptyList()).contains("30-day price history"))
    }

    @Test
    fun rendersTheHistoryTableWhenHistoryIsSupplied() {
        val history = listOf(
            PriceHistoryPoint(10.0, "2026-06-01T00:00:00.000Z"),
            PriceHistoryPoint(12.0, "2026-06-02T00:00:00.000Z"),
        )

        val html = renderSkinPage(SITE, skin(), history, emptyList())

        assertContains(html, "30-day price history")
        assertContains(html, "2026-06-01")
        assertContains(html, "$10.00")
        assertContains(html, "$12.00")
    }

    @Test
    fun linksToRelatedSkinsOfTheSameWeapon() {
        val related = listOf(
            skin().copy(id = "ak-47-vulcan-minimal-wear", marketHashName = "AK-47 | Vulcan (Minimal Wear)"),
        )

        val html = renderSkinPage(SITE, skin(), emptyList(), related)

        assertContains(html, "/skin/ak-47-vulcan-minimal-wear/")
        assertContains(html, "AK-47 | Vulcan (Minimal Wear)")
    }

    @Test
    fun escapesTheSkinNameInTheHtmlBody() {
        val dangerous = skin().copy(marketHashName = "AK-47 <script>alert(1)</script>")

        val html = renderSkinPage(SITE, dangerous, emptyList(), emptyList())

        assertFalse(html.contains("<script>alert(1)"), "raw script tag leaked into the body")
        assertContains(html, "&lt;script&gt;")
    }

    @Test
    fun neutralisesScriptTerminatorInsideJsonLd() {
        val dangerous = skin().copy(marketHashName = "AK-47 </script><img src=x onerror=alert(1)>")

        val html = renderSkinPage(SITE, dangerous, emptyList(), emptyList())

        // The ld+json block must not contain a literal </script> that would close it early.
        val jsonLdStart = html.indexOf("application/ld+json")
        assertTrue(jsonLdStart > 0)
        val firstClose = html.indexOf("</script>", jsonLdStart)
        val escapedTerminator = html.indexOf("<\\/script>", jsonLdStart)
        assertTrue(escapedTerminator in 1..<firstClose, "</script> inside JSON-LD was not escaped to <\\/script>")
    }

    @Test
    fun rendersTheStatTrakAttributeAndWear() {
        val html = renderSkinPage(SITE, skin(), emptyList(), emptyList())
        assertContains(html, "Field-Tested")
        assertContains(html, "Classified")
        assertContains(html, "AK-47")
    }

    @Test
    fun showsAnEmptyStateWhenNoMarketplaceQuoted() {
        val unpriced = skin().copy(
            skinportPrice = null, csgoMarketPrice = null, waxpeerPrice = null, lowestPrice = null,
        )

        val html = renderSkinPage(SITE, unpriced, emptyList(), emptyList())

        assertContains(html, "No marketplace is currently quoting a price")
        assertContains(html, "N/A")
    }
}
