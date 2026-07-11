package com.burixer85.cs2skinflip.generator

import com.burixer85.cs2skinflip.generator.html.renderHomePage
import com.burixer85.cs2skinflip.generator.html.renderPrivacyPage
import com.burixer85.cs2skinflip.shared.model.Skin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SITE = "https://cs2skinflip.pages.dev"

private fun skin(
    id: String,
    updatedAt: String = "2026-07-01T10:00:00.000Z",
    change: Double? = 1.0,
) = Skin(
    id = id,
    marketHashName = "AK-47 | Redline (Field-Tested)",
    weapon = "AK-47",
    rarity = "Classified",
    iconUrl = "https://cdn.example.com/x.png",
    lowestPrice = 9.99,
    priceChange24h = change,
    updatedAt = updatedAt,
)

class SitemapTest {
    @Test
    fun containsHomePrivacyAndOneUrlPerSkin() {
        val xml = renderSitemap(SITE, listOf(skin("ak"), skin("awp")))

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertContains(xml, "<loc>$SITE/</loc>")
        assertContains(xml, "<loc>$SITE/privacy/</loc>")
        assertContains(xml, "<loc>$SITE/skin/ak/</loc>")
        assertContains(xml, "<loc>$SITE/skin/awp/</loc>")
        assertEquals(4, Regex("<url>").findAll(xml).count())
    }

    @Test
    fun usesUpdatedAtDateAsLastmod() {
        val xml = renderSitemap(SITE, listOf(skin("ak", updatedAt = "2026-03-14T08:00:00.000Z")))
        assertContains(xml, "<lastmod>2026-03-14</lastmod>")
    }

    @Test
    fun escapesAmpersandsInUrls() {
        val xml = renderSitemap(SITE, listOf(skin("a&b")))
        assertContains(xml, "a&amp;b")
        assertFalse(xml.contains("<loc>$SITE/skin/a&b/</loc>"))
    }

    @Test
    fun closesTheUrlsetElement() {
        val xml = renderSitemap(SITE, emptyList())
        assertContains(xml, "</urlset>")
        assertEquals(2, Regex("<url>").findAll(xml).count())
    }
}

class RobotsTest {
    @Test
    fun allowsEverythingAndPointsAtTheSitemap() {
        val robots = renderRobots(SITE)
        assertContains(robots, "User-agent: *")
        assertContains(robots, "Allow: /")
        assertContains(robots, "Sitemap: $SITE/sitemap.xml")
    }
}

class AssetLinksTest {
    @Test
    fun declaresThePackageNameAndFingerprint() {
        val json = renderAssetLinks("AA:BB:CC")
        assertContains(json, "com.burixer85.cs2skinflip")
        assertContains(json, "delegate_permission/common.handle_all_urls")
        assertContains(json, "AA:BB:CC")
    }
}

class HomePageTest {
    @Test
    fun listsTopMoversWithLinks() {
        val html = renderHomePage(SITE, rising = listOf(skin("ak")), falling = listOf(skin("awp", change = -2.0)))

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertContains(html, "/skin/ak/")
        assertContains(html, "/skin/awp/")
        assertContains(html, "Biggest gainers")
        assertContains(html, "Biggest losers")
    }

    @Test
    fun hasACanonicalPointingAtTheRoot() {
        val html = renderHomePage(SITE, emptyList(), emptyList())
        assertContains(html, "rel=\"canonical\"")
        assertContains(html, "$SITE/")
    }

    @Test
    fun rendersWithoutMoversWithoutCrashing() {
        val html = renderHomePage(SITE, emptyList(), emptyList())
        assertContains(html, "Live CS2 skin prices")
    }
}

class PrivacyPageTest {
    @Test
    fun rendersTheContactEmailAndCanonical() {
        val html = renderPrivacyPage(SITE)

        assertContains(html, "Privacy Policy")
        assertContains(html, "burideveloper@gmail.com")
        assertContains(html, "rel=\"canonical\"")
        assertContains(html, "$SITE/privacy/")
    }

    @Test
    fun startsWithADoctype() {
        assertTrue(renderPrivacyPage(SITE).startsWith("<!DOCTYPE html>"))
    }
}
