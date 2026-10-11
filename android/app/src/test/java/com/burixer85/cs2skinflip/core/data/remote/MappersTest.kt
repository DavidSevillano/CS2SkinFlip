package com.burixer85.cs2skinflip.core.data.remote

import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class MappersTest {

    private val originalZone = TimeZone.getDefault()

    // The bug only shows off UTC: the old parser read the literal 'Z' as local time.
    @Before
    fun setUp() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))

    @After
    fun tearDown() = TimeZone.setDefault(originalZone)

    @Test
    fun `price history timestamps are read as UTC whatever the device zone`() {
        val point = PriceHistoryDto(price = 10.0, timestamp = "2026-10-11T12:00:00.000Z", source = "bulk").toDomain()

        assertEquals(1_791_720_000_000L, point!!.timestamp)
    }

    @Test
    fun `an unparseable timestamp is dropped rather than plotted at now`() {
        assertNull(PriceHistoryDto(price = 10.0, timestamp = "garbage", source = "bulk").toDomain())
    }

    @Test
    fun `top movers use the rarity the backend reports`() {
        assertEquals(SkinRarity.MIL_SPEC, topMover("AK-47 | Safari Mesh (Field-Tested)", "Mil-Spec Grade").toDomain().rarity)
    }

    @Test
    fun `knives and gloves are always the star rarity`() {
        assertEquals(SkinRarity.KNIFE, topMover("★ Karambit | Fade (Factory New)", "Covert").toDomain().rarity)
        assertEquals(SkinRarity.KNIFE, topMover("★ Sport Gloves | Vice (Field-Tested)", "Extraordinary").toDomain().rarity)
    }

    @Test
    fun `search results follow the same rarity rule as top movers`() {
        val dto = BackendSkinDto(
            id = "k", name = "n", marketHashName = "★ Karambit | Fade (Factory New)",
            weapon = "Karambit", rarity = "Covert", iconUrl = "", price = null,
        )

        assertEquals(SkinRarity.KNIFE, dto.toDomain().rarity)
    }

    private fun topMover(marketHashName: String, rarity: String?) = TopMoverDto(
        skinId = "id", marketHashName = marketHashName, name = marketHashName, iconUrl = "", rarity = rarity,
        skinportPrice = null, csgoMarketPrice = null, waxpeerPrice = null, lowestPrice = 10.0,
        priceChange24h = 5.0, volume24h = null, updatedAt = "",
    )
}
