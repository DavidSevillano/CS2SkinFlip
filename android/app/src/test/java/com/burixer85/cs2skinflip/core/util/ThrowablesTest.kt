package com.burixer85.cs2skinflip.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ThrowablesTest {

    @Test
    fun `IOException maps to a no-connection message`() {
        val error = IOException("Unable to resolve host \"cs2skinflip.onrender.com\": No address associated with hostname")

        assertEquals("No internet connection. Check your connection and try again.", error.toUserMessage())
    }

    @Test
    fun `non-IOException maps to a generic message`() {
        val error = RuntimeException("Internal Server Error")

        assertEquals("Something went wrong. Please try again.", error.toUserMessage())
    }
}
