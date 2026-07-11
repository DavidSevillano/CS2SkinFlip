package com.burixer85.cs2skinflip.generator

import com.burixer85.cs2skinflip.shared.model.Skin
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun skin(id: String, lowest: Double?, weapon: String = "AK-47") = Skin(
    id = id,
    marketHashName = "$id hash",
    weapon = weapon,
    rarity = "Classified",
    iconUrl = "https://cdn.example.com/$id.png",
    lowestPrice = lowest,
    priceChange24h = 0.0,
    updatedAt = "2026-07-01T00:00:00.000Z",
)

class SelectPagesTest {
    @Test
    fun keepsEverySkinWhenUnderTheCap() {
        val skins = listOf(skin("a", 10.0), skin("b", 5.0))
        assertEquals(skins, selectPages(skins, maxPages = 10))
    }

    @Test
    fun capsToTheMostValuableSkins() {
        val skins = listOf(skin("a", 10.0), skin("b", 5.0), skin("c", 1.0))
        assertEquals(listOf("a", "b"), selectPages(skins, maxPages = 2).map { it.id })
    }

    @Test
    fun sortsByValueBeforeCappingEvenIfInputIsUnordered() {
        val skins = listOf(skin("cheap", 1.0), skin("rich", 100.0), skin("mid", 50.0))
        assertEquals(listOf("rich", "mid"), selectPages(skins, maxPages = 2).map { it.id })
    }

    @Test
    fun treatsMissingPriceAsZero() {
        val selected = selectPages(listOf(skin("none", null), skin("priced", 1.0)), maxPages = 1)
        assertEquals(listOf("priced"), selected.map { it.id })
    }

    @Test
    fun returnsEmptyForAnEmptyExport() {
        assertEquals(emptyList(), selectPages(emptyList(), maxPages = 100))
    }
}

class HistoryTargetsTest {
    @Test
    fun picksOnlyTheTopNIds() {
        val skins = listOf(skin("a", 10.0), skin("b", 5.0), skin("c", 1.0))
        assertEquals(listOf("a", "b"), historyTargets(skins, topN = 2))
    }

    @Test
    fun handlesTopNLargerThanTheList() {
        assertEquals(listOf("a"), historyTargets(listOf(skin("a", 10.0)), topN = 5))
    }

    @Test
    fun returnsEmptyForTopNZero() {
        assertEquals(emptyList(), historyTargets(listOf(skin("a", 10.0)), topN = 0))
    }
}

class RelatedSkinsTest {
    @Test
    fun returnsOtherSkinsOfTheSameWeaponExcludingItself() {
        val target = skin("ak-redline", 10.0, weapon = "AK-47")
        val all = listOf(
            target,
            skin("ak-vulcan", 20.0, weapon = "AK-47"),
            skin("awp-asiimov", 30.0, weapon = "AWP"),
        )
        assertEquals(listOf("ak-vulcan"), relatedSkins(target, all, limit = 5).map { it.id })
    }

    @Test
    fun respectsTheLimit() {
        val target = skin("t", 1.0)
        val all = listOf(target) + (1..10).map { skin("s$it", it.toDouble()) }
        assertEquals(5, relatedSkins(target, all, limit = 5).size)
    }

    @Test
    fun returnsEmptyWhenNoSiblingWeapon() {
        val target = skin("solo", 1.0, weapon = "Zeus")
        assertEquals(emptyList(), relatedSkins(target, listOf(target), limit = 5))
    }
}

class SafeOutputDirTest {
    private val workingDir = File("/home/project").absoluteFile

    @Test
    fun rejectsTheWorkingDirectory() {
        val error = assertFailsWith<IllegalStateException> { safeOutputDir(".", workingDir) }
        assertTrue(error.message!!.contains("working directory"))
    }

    @Test
    fun rejectsTheFilesystemRoot() {
        val root = generateSequence(workingDir.normalize()) { it.parentFile }.last()
        val error = assertFailsWith<IllegalStateException> { safeOutputDir(root.absolutePath, workingDir) }
        assertTrue(error.message!!.contains("root"))
    }

    @Test
    fun allowsASubdirectory() {
        assertEquals("dist", safeOutputDir("dist", workingDir).name)
    }

    // The four scenarios below are built from a real temp directory rather than hard-coded
    // POSIX paths like "/home/runner/work/repo/web/generator": on Windows such a path has no
    // drive letter, so File.isAbsolute is false and the absolute-sibling case would be
    // mis-resolved relative to the working dir. An absolute temp base keeps them platform-neutral.
    private val tempWork = File(createTempDirectory("safeoutput").toFile(), "repo/web/generator")

    @Test
    fun rejectsTheParentOfTheWorkingDirectory() {
        assertFailsWith<IllegalStateException> { safeOutputDir("..", tempWork) }
    }

    @Test
    fun rejectsAGrandparentOfTheWorkingDirectory() {
        assertFailsWith<IllegalStateException> { safeOutputDir("../..", tempWork) }
    }

    @Test
    fun allowsAnAbsoluteSiblingPathOutsideTheWorkingDirectory() {
        val sibling = File(tempWork.parentFile, "dist")
        val dir = safeOutputDir(sibling.absolutePath, tempWork)
        assertEquals(sibling.normalize(), dir)
    }

    @Test
    fun allowsASubdirectoryOfTheWorkingDirectory() {
        val dir = safeOutputDir("dist", tempWork)
        assertEquals(File(tempWork, "dist").normalize(), dir)
    }
}

class GeneratorConfigTest {
    @Test
    fun readsRequiredVariablesAndStripsTrailingSlashes() {
        val config = GeneratorConfig.fromEnv(
            mapOf("BACKEND_URL" to "https://api.example.com/", "SITE_URL" to "https://site.example/"),
        )
        assertEquals("https://api.example.com", config.backendUrl)
        assertEquals("https://site.example", config.siteUrl)
        assertEquals("dist", config.outputDir)
        assertTrue(config.assetLinksFingerprint.isNotBlank())
    }

    @Test
    fun honoursOptionalOverrides() {
        val config = GeneratorConfig.fromEnv(
            mapOf(
                "BACKEND_URL" to "https://api.example.com",
                "SITE_URL" to "https://site.example",
                "OUT_DIR" to "out",
                "ANDROID_CERT_SHA256" to "AA:BB",
            ),
        )
        assertEquals("out", config.outputDir)
        assertEquals("AA:BB", config.assetLinksFingerprint)
    }

    @Test
    fun failsLoudlyWhenBackendUrlIsMissing() {
        val error = assertFailsWith<IllegalStateException> {
            GeneratorConfig.fromEnv(mapOf("SITE_URL" to "https://site.example"))
        }
        assertTrue(error.message!!.contains("BACKEND_URL"))
    }

    @Test
    fun failsLoudlyWhenSiteUrlIsBlank() {
        assertFailsWith<IllegalStateException> {
            GeneratorConfig.fromEnv(mapOf("BACKEND_URL" to "https://api.example.com", "SITE_URL" to "   "))
        }
    }
}
