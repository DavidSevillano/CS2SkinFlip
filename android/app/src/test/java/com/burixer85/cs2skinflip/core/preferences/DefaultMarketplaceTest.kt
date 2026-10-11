package com.burixer85.cs2skinflip.core.preferences

import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultMarketplaceTest {

    private val skin = Skin(
        id = "ak", name = "AK-47 | Redline", marketHashName = "AK-47 | Redline (Field-Tested)", weapon = "AK-47",
        skinName = "Redline", rarity = SkinRarity.CLASSIFIED, wear = SkinWear.FIELD_TESTED, imageUrl = "",
        skinportPrice = 24.93, csgoMarketPrice = 27.66, waxpeerPrice = null, csfloatPrice = 25.19,
        lowestPrice = 24.93, priceChange24h = null, volume24h = 0, floatMin = 0f, floatMax = 1f, floatMedian = 0.5f,
    )

    @Test
    fun `each marketplace shows its own quote`() {
        assertEquals(24.93, DefaultMarketplace.SKINPORT.priceOf(skin), 0.0)
        assertEquals(27.66, DefaultMarketplace.CSGO_MARKET.priceOf(skin), 0.0)
        assertEquals(25.19, DefaultMarketplace.CSFLOAT.priceOf(skin), 0.0)
    }

    @Test
    fun `always cheapest shows the lowest price`() {
        assertEquals(24.93, DefaultMarketplace.LOWEST.priceOf(skin), 0.0)
    }

    // A dash would hide a price the user can act on elsewhere.
    @Test
    fun `falls back to the lowest price where the marketplace does not list the skin`() {
        assertEquals(24.93, DefaultMarketplace.WAXPEER.priceOf(skin), 0.0)
    }
}
