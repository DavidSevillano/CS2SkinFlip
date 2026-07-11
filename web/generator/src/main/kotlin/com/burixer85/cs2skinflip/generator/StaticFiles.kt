package com.burixer85.cs2skinflip.generator

import com.burixer85.cs2skinflip.shared.model.Skin

const val ANDROID_PACKAGE = "com.burixer85.cs2skinflip"

/** The five predefined XML entities. Skin ids are slugs, but never trust them in markup. */
private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun urlEntry(loc: String, lastmod: String?, changefreq: String, priority: String): String =
    buildString {
        append("  <url>\n")
        append("    <loc>${xmlEscape(loc)}</loc>\n")
        if (lastmod != null) append("    <lastmod>$lastmod</lastmod>\n")
        append("    <changefreq>$changefreq</changefreq>\n")
        append("    <priority>$priority</priority>\n")
        append("  </url>\n")
    }

fun renderSitemap(siteUrl: String, skins: List<Skin>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append('\n')
    append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">").append('\n')
    append(urlEntry("$siteUrl/", null, "daily", "1.0"))
    append(urlEntry("$siteUrl/privacy/", null, "yearly", "0.1"))
    skins.forEach { skin ->
        append(urlEntry("$siteUrl/skin/${skin.id}/", skin.updatedAt.take(10), "daily", "0.7"))
    }
    append("</urlset>\n")
}

fun renderRobots(siteUrl: String): String = """
    User-agent: *
    Allow: /

    Sitemap: $siteUrl/sitemap.xml
""".trimIndent() + "\n"

/**
 * Android App Links verification. Until the release keystore's SHA-256 is supplied the
 * fingerprint is a placeholder and App Links stay unverified — the site still works.
 */
fun renderAssetLinks(sha256Fingerprint: String): String = """
    [
      {
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
          "namespace": "android_app",
          "package_name": "$ANDROID_PACKAGE",
          "sha256_cert_fingerprints": ["$sha256Fingerprint"]
        }
      }
    ]
""".trimIndent() + "\n"
