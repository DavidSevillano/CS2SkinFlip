package com.burixer85.cs2skinflip.core.util

import com.burixer85.cs2skinflip.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ThrowablesTest {

    @Test
    fun `IOException maps to a no-connection message`() {
        val error = IOException("Unable to resolve host \"cs2skinflip.onrender.com\": No address associated with hostname")

        assertEquals(R.string.error_no_internet, error.toUserMessageRes())
    }

    @Test
    fun `non-IOException maps to a generic message`() {
        val error = RuntimeException("Internal Server Error")

        assertEquals(R.string.error_generic_retry, error.toUserMessageRes())
    }
}
