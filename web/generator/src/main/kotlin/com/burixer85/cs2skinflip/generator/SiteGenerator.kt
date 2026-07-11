package com.burixer85.cs2skinflip.generator

import com.burixer85.cs2skinflip.generator.html.renderHomePage
import com.burixer85.cs2skinflip.generator.html.renderPrivacyPage
import com.burixer85.cs2skinflip.generator.html.renderSkinPage
import com.burixer85.cs2skinflip.shared.api.Cs2ApiClient
import com.burixer85.cs2skinflip.shared.model.PriceHistoryPoint
import com.burixer85.cs2skinflip.shared.model.Skin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** Most valuable first, then truncated to the deployment's file budget. */
fun selectPages(skins: List<Skin>, maxPages: Int): List<Skin> =
    skins.sortedByDescending { it.lowestPrice ?: 0.0 }.take(maxPages)

/** Ids of the pages that earn a price-history table. Assumes [skins] is already value-sorted. */
fun historyTargets(skins: List<Skin>, topN: Int): List<String> =
    skins.take(topN).map { it.id }

/**
 * "You might also like" siblings for a skin page. Takes the first [limit] same-weapon skins in
 * list order — which, because [selectPages] hands us a value-descending list, means the most
 * valuable siblings. Intentionally cheap: no similarity scoring.
 */
fun relatedSkins(skin: Skin, all: List<Skin>, limit: Int): List<Skin> =
    all.asSequence()
        .filter { it.weapon == skin.weapon && it.id != skin.id }
        .take(limit)
        .toList()

/**
 * Resolves [path] to an absolute directory and refuses to hand [SiteGenerator.generate]'s
 * `deleteRecursively` a directory that is the working directory or any ancestor of it (up to
 * and including the filesystem root). Concretely `OUT_DIR=/` would wipe the filesystem root,
 * `OUT_DIR=.` the working directory, and `OUT_DIR=..` its parent (e.g. the repo root when the
 * CWD is a module subdirectory). A sibling or otherwise unrelated absolute path — such as CI's
 * `<workspace>/web/dist` while the CWD is `web/generator` — is not an ancestor and is allowed.
 */
fun safeOutputDir(path: String, workingDir: File = File("").absoluteFile): File {
    val work = workingDir.normalize()
    val dir = File(path).let { if (it.isAbsolute) it else File(work, path) }.normalize()
    check(dir.parentFile != null) { "Refusing to use the filesystem root as OUT_DIR: $dir" }
    check(!work.toPath().startsWith(dir.toPath())) {
        "Refusing to use the working directory or an ancestor of it as OUT_DIR: $dir"
    }
    return dir
}

class SiteGenerator(
    private val api: Cs2ApiClient,
    private val config: GeneratorConfig,
    private val log: (String) -> Unit = ::println,
) {
    suspend fun generate() {
        log("Fetching skin export from ${config.backendUrl} ...")
        val export = api.fetchExport()
        log("Backend returned ${export.size} priced skins.")

        val pages = selectPages(export, MAX_SKIN_PAGES)
        if (export.size > MAX_SKIN_PAGES) {
            log(
                "WARNING: ${export.size} priced skins exceeds the $MAX_SKIN_PAGES page budget " +
                    "(Cloudflare Pages allows 20k files per deployment). Generating the " +
                    "${pages.size} most valuable and skipping ${export.size - pages.size}.",
            )
        }

        val history = fetchHistories(historyTargets(pages, HISTORY_TOP_N))
        log("Fetched price history for ${history.size} skins.")

        val out = safeOutputDir(config.outputDir)
        out.deleteRecursively()
        out.mkdirs()

        pages.forEach { skin ->
            val html = renderSkinPage(
                siteUrl = config.siteUrl,
                skin = skin,
                history = history[skin.id].orEmpty(),
                related = relatedSkins(skin, pages, RELATED_SKINS_LIMIT),
            )
            write(File(out, "skin/${skin.id}/index.html"), html)
        }
        log("Wrote ${pages.size} skin pages.")

        val movers = pages.filter { it.priceChange24h != null }
        val rising = movers.sortedByDescending { it.priceChange24h }.take(10)
        val falling = movers.sortedBy { it.priceChange24h }.take(10)

        write(File(out, "index.html"), renderHomePage(config.siteUrl, rising, falling))
        write(File(out, "privacy/index.html"), renderPrivacyPage(config.siteUrl))
        write(File(out, "sitemap.xml"), renderSitemap(config.siteUrl, pages))
        write(File(out, "robots.txt"), renderRobots(config.siteUrl))
        write(File(out, ".well-known/assetlinks.json"), renderAssetLinks(config.assetLinksFingerprint))

        val fileCount = out.walkTopDown().count { it.isFile }
        log("Done. $fileCount files in ${config.outputDir}/.")
        if (fileCount > 20_000) {
            log("WARNING: $fileCount files exceeds Cloudflare Pages' 20,000-file deployment limit.")
        }
    }

    /** Bounded concurrency: the backend rate-limits per IP, and a failed chart must not fail the build. */
    private suspend fun fetchHistories(ids: List<String>): Map<String, List<PriceHistoryPoint>> =
        coroutineScope {
            val gate = Semaphore(HISTORY_CONCURRENCY)
            ids.map { id ->
                async {
                    gate.withPermit {
                        id to runCatching { api.fetchPriceHistory(id, "30d") }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().filter { it.second.isNotEmpty() }.toMap()
        }

    private fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
