package com.burixer85.cs2skinflip.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CurrencyTest {

    // The portfolio showed a loss as "—" because format() treats ≤ 0 as unknown.
    @Test
    fun `formatChange shows losses and gains with their sign`() {
        Locale.setDefault(Locale.US)

        assertEquals("-$0.01", Currency.formatChange(-0.01))
        assertEquals("+$12.50", Currency.formatChange(12.5))
        assertEquals("+$0.00", Currency.formatChange(0.0))
    }
}
