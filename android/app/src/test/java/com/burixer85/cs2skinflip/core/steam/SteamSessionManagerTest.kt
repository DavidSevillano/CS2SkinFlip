package com.burixer85.cs2skinflip.core.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class SteamSessionManagerTest {

    private fun manager(cookies: String?): SteamSessionManager =
        SteamSessionManager(mock()).apply { readCookies = { cookies } }

    @Test
    fun `no cookies at all means not connected`() {
        val manager = manager(null)

        assertNull(manager.sessionCookieHeader())
        assertFalse(manager.isConnected())
    }

    @Test
    fun `anonymous browse cookies without a login are not a session`() {
        val manager = manager("sessionid=abc123; timezoneOffset=7200,0")

        assertNull(manager.sessionCookieHeader())
        assertFalse(manager.isConnected())
    }

    @Test
    fun `a steamLoginSecure cookie makes the full header available`() {
        val cookies = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Ctoken"
        val manager = manager(cookies)

        assertEquals(cookies, manager.sessionCookieHeader())
        assertTrue(manager.isConnected())
    }

    @Test
    fun `disconnect expires the auth cookies in the jar`() {
        val written = mutableListOf<String>()
        val manager = SteamSessionManager(mock()).apply {
            readCookies = { "steamLoginSecure=tok; sessionid=abc" }
            writeCookie = { _, cookie -> written.add(cookie) }
        }

        manager.disconnect()

        assertTrue(written.any { it.startsWith("steamLoginSecure=") && it.contains("1970") })
        assertTrue(written.any { it.startsWith("sessionid=") && it.contains("1970") })
    }
}
