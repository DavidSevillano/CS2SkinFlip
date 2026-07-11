package com.burixer85.cs2skinflip.shared.format

import com.burixer85.cs2skinflip.shared.model.Skin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun skin(
    skinport: Double? = null,
    csgoMarket: Double? = null,
    waxpeer: Double? = null,
    lowest: Double? = null,
) = Skin(
    id = "ak-47-redline-field-tested",
    marketHashName = "AK-47 | Redline (Field-Tested)",
    weapon = "AK-47",
    rarity = "Classified",
    iconUrl = "https://cdn.example.com/x.png",
    skinportPrice = skinport,
    csgoMarketPrice = csgoMarket,
    waxpeerPrice = waxpeer,
    lowestPrice = lowest,
    priceChange24h = null,
    updatedAt = "2026-07-01T00:00:00.000Z",
)

class ParseWearTest {
    @Test
    fun extractsKnownWearFromTrailingParenthetical() {
        assertEquals("Field-Tested", parseWear("AK-47 | Redline (Field-Tested)"))
        assertEquals("Factory New", parseWear("★ StatTrak™ Karambit | Fade (Factory New)"))
    }

    @Test
    fun returnsNullWhenParentheticalIsNotAWear() {
        assertNull(parseWear("Sticker | Titan (Holo) | Katowice 2014"))
    }

    @Test
    fun returnsNullWhenThereIsNoParenthetical() {
        assertNull(parseWear("AK-47 | Redline"))
    }
}

class IsStatTrakTest {
    @Test
    fun detectsStatTrak() {
        assertTrue(isStatTrak("StatTrak™ AK-47 | Redline (Field-Tested)"))
        assertTrue(isStatTrak("★ StatTrak™ Karambit | Fade (Factory New)"))
    }

    @Test
    fun isFalseForPlainSkins() {
        assertFalse(isStatTrak("AK-47 | Redline (Field-Tested)"))
    }
}

class MarketplaceEntriesTest {
    @Test
    fun omitsNullAndNonPositivePrices() {
        assertEquals(
            listOf(MarketplaceEntry("Waxpeer", 5.0)),
            marketplaceEntries(skin(skinport = 0.0, csgoMarket = null, waxpeer = 5.0)),
        )
    }

    @Test
    fun sortsCheapestFirst() {
        assertEquals(
            listOf(
                MarketplaceEntry("CS:GO Market", 9.99),
                MarketplaceEntry("Waxpeer", 11.0),
                MarketplaceEntry("Skinport", 12.5),
            ),
            marketplaceEntries(skin(skinport = 12.5, csgoMarket = 9.99, waxpeer = 11.0)),
        )
    }

    @Test
    fun isEmptyWhenNoMarketplaceQuoted() {
        assertEquals(emptyList(), marketplaceEntries(skin()))
    }
}

class FormatPriceTest {
    @Test
    fun rendersTwoDecimalsWithDollarSign() {
        assertEquals("$12.50", formatPrice(12.5))
        assertEquals("$9.99", formatPrice(9.99))
        assertEquals("$0.07", formatPrice(0.07))
    }

    @Test
    fun rendersNaForMissingPrice() {
        assertEquals("N/A", formatPrice(null))
    }

    @Test
    fun roundsAndCarriesIntoTheWholePart() {
        assertEquals("$1.00", formatPrice(0.999))
        assertEquals("$10.00", formatPrice(9.999))
    }

    @Test
    fun rendersLargeValues() {
        assertEquals("$12345.68", formatPrice(12345.678))
    }
}

class DecimalStringTest {
    @Test
    fun rendersABareTwoDecimalNumber() {
        assertEquals("9.99", decimalString(9.99))
        assertEquals("1.00", decimalString(0.999))
        assertEquals("0.00", decimalString(-0.001))
        assertEquals("-2.10", decimalString(-2.1))
    }
}

class FormatChangeTest {
    @Test
    fun prefixesPositiveChangeWithPlus() {
        assertEquals("+3.46%", formatChange(3.456))
    }

    @Test
    fun keepsMinusSignOnNegativeChange() {
        assertEquals("-2.10%", formatChange(-2.1))
    }

    @Test
    fun rendersZeroWithoutSign() {
        assertEquals("0.00%", formatChange(0.0))
    }

    @Test
    fun returnsNullWhenThereIsNoChangeData() {
        assertNull(formatChange(null))
    }
}
