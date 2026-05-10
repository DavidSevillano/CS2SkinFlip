package com.burixer85.cs2skinflip.core.data.mock

import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import com.burixer85.cs2skinflip.core.domain.model.PricePoint
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import kotlin.math.abs

// Steam CDN skin images (publicly accessible)
private const val CDN = "https://community.fastly.steamstatic.com/economy/image"

object MockData {

    val skins: List<Skin> = listOf(
        Skin(
            id = "ak47_redline",
            name = "AK-47 | Redline",
            weapon = "AK-47",
            skinName = "Redline",
            rarity = SkinRarity.CLASSIFIED,
            wear = SkinWear.FIELD_TESTED,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpot7HxfDhjxszJemkV09-5lpKKqPrxN7LEmwJQ7MMpiLuSoo_3i1C8-Bc5MTzyJIOWdlRsYQ7U-Vm5ku_njZC5vZ_LnHowtXE8pSGKlQ",
            cs2capPrice = 23.10,            csfloatPrice = 21.95,
            csgoMarketPrice = 22.30,
            priceChange24h = 2.3,
            volume24h = 1450,
            floatMin = 0.15f,
            floatMax = 0.37f,
            floatMedian = 0.23f,
            collection = "The Phoenix Collection",
            priceHistory = generatePriceHistory(24.50, 30)
        ),
        Skin(
            id = "awp_asiimov",
            name = "AWP | Asiimov",
            weapon = "AWP",
            skinName = "Asiimov",
            rarity = SkinRarity.COVERT,
            wear = SkinWear.FIELD_TESTED,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpot621FAR17PLfYQJD_9W7m5a0mvLwOq7c2DoI6p0h3rrE8Y3mitW1rxBjYG3qIoWRIAA4YV_SrFa-xO_shJW7vM6czCZiuCkqs2GdwUJBZyh9",
            cs2capPrice = 62.80,            csfloatPrice = 60.40,
            csgoMarketPrice = 61.50,
            priceChange24h = -1.8,
            volume24h = 890,
            floatMin = 0.18f,
            floatMax = 1.00f,
            floatMedian = 0.45f,
            collection = "The Phoenix Collection",
            priceHistory = generatePriceHistory(65.20, 30)
        ),
        Skin(
            id = "m4a4_howl",
            name = "M4A4 | Howl",
            weapon = "M4A4",
            skinName = "Howl",
            rarity = SkinRarity.CONTRABAND,
            wear = SkinWear.FIELD_TESTED,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpou-6kejhnwMzFJTwW09-vloWZh8j1OqzIgWoG65Zy2LyZp9ys2wKy_Us-YWn3INCQdFJoZF3S-lG9le_vhcC5vJvN13s3nXIsjDlmQrk",
            cs2capPrice = 2050.00,            csfloatPrice = 1950.00,
            csgoMarketPrice = 1980.00,
            priceChange24h = 0.8,
            volume24h = 12,
            floatMin = 0.00f,
            floatMax = 0.44f,
            floatMedian = 0.18f,
            collection = "Contraband",
            priceHistory = generatePriceHistory(2100.00, 30)
        ),
        Skin(
            id = "karambit_doppler",
            name = "★ Karambit | Doppler",
            weapon = "Knife",
            skinName = "Doppler",
            rarity = SkinRarity.KNIFE,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpovbSsLQJf0ebcZThQ6t2zkIaZhsr_DK_Lg2yew9Wp9I_go9n231nkqRVuYz37cNjHIQFsNVuD_FO_xL_n15-5tZ3XnCFr6Cke93xYuQ",
            cs2capPrice = 755.00,            csfloatPrice = 730.00,
            csgoMarketPrice = 742.00,
            priceChange24h = 3.5,
            volume24h = 45,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.03f,
            collection = "The Chroma 2 Case",
            priceHistory = generatePriceHistory(780.00, 30)
        ),
        Skin(
            id = "glock_fade",
            name = "Glock-18 | Fade",
            weapon = "Glock-18",
            skinName = "Fade",
            rarity = SkinRarity.RESTRICTED,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgposbaqKAxf0v73cCVQ74eKlY20kfbmMbfUqWdQ-sJ0xOuYoNijiVe3uEorNzH6cYeRJlVpN1nY_ge_wu_ug8Pv7czN12s2nCFuvlM",
            cs2capPrice = 308.00,            csfloatPrice = 295.00,
            csgoMarketPrice = 299.00,
            priceChange24h = -0.5,
            volume24h = 78,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.02f,
            priceHistory = generatePriceHistory(315.00, 30)
        ),
        Skin(
            id = "ak47_case_hardened",
            name = "AK-47 | Case Hardened",
            weapon = "AK-47",
            skinName = "Case Hardened",
            rarity = SkinRarity.CLASSIFIED,
            wear = SkinWear.FIELD_TESTED,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpot7HxfDhjxszJemkV09-5lpKKqPrxN7LEmwJQ7MMpiLuSoo_3i1C8-Bc5MTyzIIaRd1Q5NV3Srla5x-m5hJ-47JrIm3U83SFQyQ",
            cs2capPrice = 17.80,            csfloatPrice = 17.20,
            csgoMarketPrice = 17.45,
            priceChange24h = 5.2,
            volume24h = 2300,
            floatMin = 0.06f,
            floatMax = 0.80f,
            floatMedian = 0.32f,
            priceHistory = generatePriceHistory(18.90, 30)
        ),
        Skin(
            id = "p250_sand_dune",
            name = "P250 | Sand Dune",
            weapon = "P250",
            skinName = "Sand Dune",
            rarity = SkinRarity.CONSUMER,
            wear = SkinWear.FIELD_TESTED,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpopuP1FABz7ePdfi9Q6sqAkIGZmvbmNrfUqWdQ-sJ0xOuYoNimjlS1_ktrMTChdoTFIgJqNFGCq1i5yrju1ZO4u8jAnSY2pHUl-EUYTg",
            cs2capPrice = 0.38,            csfloatPrice = 0.36,
            csgoMarketPrice = 0.37,
            priceChange24h = -3.1,
            volume24h = 8900,
            floatMin = 0.00f,
            floatMax = 1.00f,
            floatMedian = 0.50f,
            priceHistory = generatePriceHistory(0.45, 30)
        ),
        Skin(
            id = "m4a1s_printstream",
            name = "M4A1-S | Printstream",
            weapon = "M4A1-S",
            skinName = "Printstream",
            rarity = SkinRarity.COVERT,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpou-6kejhjxMzcdShQ7N2zkIaZh8j1OqzIgWoG65Zy2LyZp9ys2wKy_Us-YWuuJNCSdlU-YluCq1K_xO_vjpS5vZubnXFkuH0Eknr3Q",
            cs2capPrice = 122.50,            csfloatPrice = 118.90,
            csgoMarketPrice = 120.10,
            priceChange24h = 1.2,
            volume24h = 320,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.02f,
            priceHistory = generatePriceHistory(125.40, 30)
        ),
        Skin(
            id = "desert_eagle_blaze",
            name = "Desert Eagle | Blaze",
            weapon = "Desert Eagle",
            skinName = "Blaze",
            rarity = SkinRarity.RESTRICTED,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgposr-3LghfwMvcYepHvYyJmImMn-O6Ma_um2pf4sN0xD-ToYr82wLh_BFpZWGncYCXIgRqYV6D_FDvk-i71J_p75nM1TFg7Cw50Jky1Q",
            cs2capPrice = 422.00,            csfloatPrice = 410.00,
            csgoMarketPrice = 415.00,
            priceChange24h = 4.1,
            volume24h = 62,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.01f,
            priceHistory = generatePriceHistory(428.00, 30)
        ),
        Skin(
            id = "usp_kill_confirmed",
            name = "USP-S | Kill Confirmed",
            weapon = "USP-S",
            skinName = "Kill Confirmed",
            rarity = SkinRarity.COVERT,
            wear = SkinWear.MINIMAL_WEAR,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpoo6m1FBRp3_bGcjhQ09-knpassGkh4LfDqWdQ-sJ0xOuYoNimjlKu4rBVuNW_dI-OQcVJoZF3S-lG9le_vhRjPGIuow",
            cs2capPrice = 46.80,            csfloatPrice = 44.70,
            csgoMarketPrice = 45.50,
            priceChange24h = -0.9,
            volume24h = 540,
            floatMin = 0.07f,
            floatMax = 0.15f,
            floatMedian = 0.11f,
            priceHistory = generatePriceHistory(48.30, 30)
        ),
        Skin(
            id = "awp_dragon_lore",
            name = "AWP | Dragon Lore",
            weapon = "AWP",
            skinName = "Dragon Lore",
            rarity = SkinRarity.COVERT,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpot621FAZu7ObdGQJD_9W7m5a0mvLwOq7c2DoI6p0h3rrE8Y3mitW1rxBjYG3qIoWRIAA4YV_SrFa-xO_shJW7vM6czCZktnpPOEWb",
            cs2capPrice = 1820.00,            csfloatPrice = 1760.00,
            csgoMarketPrice = 1790.00,
            priceChange24h = -1.2,
            volume24h = 8,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.02f,
            collection = "The Cobblestone Collection",
            priceHistory = generatePriceHistory(1840.00, 30)
        ),
        Skin(
            id = "ak47_fire_serpent",
            name = "AK-47 | Fire Serpent",
            weapon = "AK-47",
            skinName = "Fire Serpent",
            rarity = SkinRarity.COVERT,
            wear = SkinWear.FIELD_TESTED,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpot7HxfDhjxszJemkV09-5lpKKqPrxN7LEmwJQ7MMpiLuSoo_3i1C8-Bc5MTylJo-QIlA2YwjR-lW2xuy_hMK4u5_OwXFssnJp0Yvk8A",
            cs2capPrice = 408.00,            csfloatPrice = 392.00,
            csgoMarketPrice = 398.00,
            priceChange24h = 6.8,
            volume24h = 28,
            floatMin = 0.15f,
            floatMax = 0.38f,
            floatMedian = 0.25f,
            collection = "The Overpass Collection",
            priceHistory = generatePriceHistory(420.00, 30)
        ),
        Skin(
            id = "butterfly_marble_fade",
            name = "★ Butterfly Knife | Marble Fade",
            weapon = "Knife",
            skinName = "Marble Fade",
            rarity = SkinRarity.KNIFE,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpovbSsLQJf0ebcZThQ6t2zkIaZhsr_DK_Lg2yew9Wp9I_go9n231nkqRVuYz37cNjHIQFsNVuD_FO_xL_n15-5tZ3XnCFr6Cke93xYuQ",
            cs2capPrice = 1130.00,            csfloatPrice = 1085.00,
            csgoMarketPrice = 1100.00,
            priceChange24h = 2.1,
            volume24h = 18,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.01f,
            priceHistory = generatePriceHistory(1150.00, 30)
        ),
        Skin(
            id = "mp9_hot_rod",
            name = "MP9 | Hot Rod",
            weapon = "MP9",
            skinName = "Hot Rod",
            rarity = SkinRarity.CLASSIFIED,
            wear = SkinWear.FACTORY_NEW,
            imageUrl = "$CDN/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLQHTxDZ7I56KU0Zwwo4NUX4oFJZEHLbXH5ApeO4YmlhxYQknCRvCo04DEVlxkKgpo1saZlhR7nOjcdDpX-t6zi4m0hOCgNqjQhm4G7sMmiLqZrY-j2FK3rhFkZWGhd4TIcFZpYAqD_VK_w-i90Je1vJvN1mtq63Ns52w",
            cs2capPrice = 178.00,            csfloatPrice = 171.00,
            csgoMarketPrice = 174.00,
            priceChange24h = 3.9,
            volume24h = 95,
            floatMin = 0.00f,
            floatMax = 0.08f,
            floatMedian = 0.01f,
            priceHistory = generatePriceHistory(182.00, 30)
        )
    )

    val trendingSkins: List<Skin>
        get() = skins.sortedByDescending { abs(it.priceChange24h ?: 0.0) }.take(10)

    val mockWatchlist: List<WatchlistItem> = listOf(
        WatchlistItem(
            id = 1,
            skinId = "m4a4_howl",
            skinName = "M4A4 | Howl (Field-Tested)",
            skinImageUrl = skins[2].imageUrl,
            targetBuyPrice = 1950.00,
            targetSellPrice = null,
            currentPrice = 2100.00,
            priceChange24h = 0.8,
            alertEnabled = true
        ),
        WatchlistItem(
            id = 2,
            skinId = "awp_dragon_lore",
            skinName = "AWP | Dragon Lore (Factory New)",
            skinImageUrl = skins[10].imageUrl,
            targetBuyPrice = 1700.00,
            targetSellPrice = null,
            currentPrice = 1840.00,
            priceChange24h = -1.2,
            alertEnabled = true
        ),
        WatchlistItem(
            id = 3,
            skinId = "glock_fade",
            skinName = "Glock-18 | Fade (Factory New)",
            skinImageUrl = skins[4].imageUrl,
            targetBuyPrice = null,
            targetSellPrice = 350.00,
            currentPrice = 315.00,
            priceChange24h = -0.5,
            alertEnabled = false
        ),
        WatchlistItem(
            id = 4,
            skinId = "butterfly_marble_fade",
            skinName = "★ Butterfly Knife | Marble Fade (FN)",
            skinImageUrl = skins[12].imageUrl,
            targetBuyPrice = 1050.00,
            targetSellPrice = null,
            currentPrice = 1150.00,
            priceChange24h = 2.1,
            alertEnabled = true
        )
    )

    val mockAlerts: List<Alert> = listOf(
        Alert(
            id = "1",
            skinId = "m4a4_howl",
            skinName = "M4A4 | Howl (FT)",
            skinImageUrl = skins[2].imageUrl,
            type = AlertType.BUY_BELOW,
            targetPrice = 1950.00,
            currentPrice = 2100.00,
            isActive = true,
            isTriggered = false
        ),
        Alert(
            id = "2",
            skinId = "ak47_fire_serpent",
            skinName = "AK-47 | Fire Serpent (FT)",
            skinImageUrl = skins[11].imageUrl,
            type = AlertType.SELL_ABOVE,
            targetPrice = 450.00,
            currentPrice = 420.00,
            isActive = true,
            isTriggered = false
        ),
        Alert(
            id = "3",
            skinId = "awp_asiimov",
            skinName = "AWP | Asiimov (FT)",
            skinImageUrl = skins[1].imageUrl,
            type = AlertType.BUY_BELOW,
            targetPrice = 60.00,
            currentPrice = 65.20,
            isActive = false,
            isTriggered = false
        )
    )

    fun getSkinById(id: String): Skin? = skins.find { it.id == id }

    fun searchSkins(query: String): List<Skin> {
        if (query.isBlank()) return skins
        val lower = query.lowercase()
        return skins.filter {
            it.name.lowercase().contains(lower) ||
                it.weapon.lowercase().contains(lower) ||
                it.skinName.lowercase().contains(lower)
        }
    }

    private fun generatePriceHistory(currentPrice: Double, days: Int): List<PricePoint> {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        return (days downTo 0).map { i ->
            val noise = (Math.random() - 0.5) * 0.1 * currentPrice
            PricePoint(
                timestamp = now - i * dayMs,
                price = maxOf(0.01, currentPrice + noise * (i.toDouble() / days))
            )
        }
    }
}
