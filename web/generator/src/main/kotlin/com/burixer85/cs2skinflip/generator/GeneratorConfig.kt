package com.burixer85.cs2skinflip.generator

/**
 * Cloudflare Pages accepts at most 20,000 files per deployment. `dist/` also holds
 * index.html, privacy/index.html, sitemap.xml, robots.txt and assetlinks.json, so the
 * skin-page budget is capped below that with headroom.
 */
const val MAX_SKIN_PAGES = 19_500

/** Price history costs one HTTP request per skin, so only the highest-value pages get it. */
const val HISTORY_TOP_N = 1_000

/** Concurrent price-history requests. Kept low: the backend rate-limits per IP. */
const val HISTORY_CONCURRENCY = 8

const val RELATED_SKINS_LIMIT = 6

data class GeneratorConfig(
    val backendUrl: String,
    val siteUrl: String,
    val outputDir: String,
    val assetLinksFingerprint: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String>): GeneratorConfig {
            fun required(key: String): String {
                val raw = env[key]?.trim()
                check(!raw.isNullOrBlank()) { "Missing required environment variable: $key" }
                return raw.trimEnd('/')
            }

            return GeneratorConfig(
                backendUrl = required("BACKEND_URL"),
                siteUrl = required("SITE_URL"),
                outputDir = env["OUT_DIR"]?.takeIf { it.isNotBlank() } ?: "dist",
                assetLinksFingerprint = env["ANDROID_CERT_SHA256"]?.takeIf { it.isNotBlank() }
                    ?: "REPLACE_WITH_RELEASE_SHA256_FINGERPRINT",
            )
        }
    }
}
