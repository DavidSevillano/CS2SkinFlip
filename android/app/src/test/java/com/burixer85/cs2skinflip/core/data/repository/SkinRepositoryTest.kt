package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MarketPriceOverviewResponse
import com.burixer85.cs2skinflip.core.data.remote.SteamApiService
import com.burixer85.cs2skinflip.core.steam.SteamSessionManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SkinRepositoryTest {

    private fun anonymousSession() = SteamSessionManager(mock()).apply {
        readCookies = { null }
        userAgentProvider = { "test-ua" }
    }

    private fun connectedSession(cookies: String) = SteamSessionManager(mock()).apply {
        readCookies = { cookies }
        userAgentProvider = { "test-ua" }
    }

    @Test
    fun `getTrendingSkins propagates the failure instead of returning mock data`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getTopMovers("rising")).thenThrow(RuntimeException("no network"))
        val repository = SkinRepository(backendApi, mock(), anonymousSession())

        var caught: Throwable? = null
        repository.getTrendingSkins()
            .catch { e -> caught = e }
            .toList()

        assertTrue(caught is RuntimeException)
    }

    @Test
    fun `getTrendingSkins forwards the direction param to the API`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getTopMovers("falling")).thenThrow(RuntimeException("stop"))
        val repository = SkinRepository(backendApi, mock(), anonymousSession())

        var caught: Throwable? = null
        repository.getTrendingSkins(direction = "falling")
            .catch { e -> caught = e }
            .toList()

        assertTrue(caught is RuntimeException)
    }

    @Test
    fun `getSkinById propagates the failure instead of returning mock data`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getSkin("ak-47-redline")).thenThrow(RuntimeException("no network"))
        val repository = SkinRepository(backendApi, mock(), anonymousSession())

        var threw = false
        try {
            repository.getSkinById("ak-47-redline")
        } catch (e: RuntimeException) {
            threw = true
        }

        assertTrue(threw)
    }

    @Test
    fun `getAllWeapons propagates the failure instead of returning mock data`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getWeapons()).thenThrow(RuntimeException("no network"))
        val repository = SkinRepository(backendApi, mock(), anonymousSession())

        var threw = false
        try {
            repository.getAllWeapons()
        } catch (e: RuntimeException) {
            threw = true
        }

        assertTrue(threw)
    }

    @Test
    fun `getSteamPrice calls Steam directly and returns the parsed price on success`() = runBlocking {
        val steamApi = mock<SteamApiService>()
        whenever(steamApi.getMarketPriceOverview(marketHashName = "AK-47 | Redline (Field-Tested)", userAgent = "test-ua"))
            .thenReturn(MarketPriceOverviewResponse(success = true, lowest_price = "\$32.39", median_price = null, volume = null))
        val repository = SkinRepository(mock(), steamApi, anonymousSession())

        val price = repository.getSteamPrice("AK-47 | Redline (Field-Tested)")

        assertEquals(32.39, price)
    }

    @Test
    fun `getSteamPrice parses a price with a thousands separator`() = runBlocking {
        val steamApi = mock<SteamApiService>()
        whenever(steamApi.getMarketPriceOverview(marketHashName = "★ Karambit | Doppler (Factory New)", userAgent = "test-ua"))
            .thenReturn(MarketPriceOverviewResponse(success = true, lowest_price = "\$1,234.56", median_price = null, volume = null))
        val repository = SkinRepository(mock(), steamApi, anonymousSession())

        val price = repository.getSteamPrice("★ Karambit | Doppler (Factory New)")

        assertEquals(1234.56, price)
    }

    @Test
    fun `getSteamPrice falls back to median_price when Steam omits lowest_price`() = runBlocking {
        val steamApi = mock<SteamApiService>()
        whenever(steamApi.getMarketPriceOverview(marketHashName = "M4A4 | Asiimov (Field-Tested)", userAgent = "test-ua"))
            .thenReturn(MarketPriceOverviewResponse(success = true, lowest_price = null, median_price = "\$317.14", volume = "2"))
        val repository = SkinRepository(mock(), steamApi, anonymousSession())

        val price = repository.getSteamPrice("M4A4 | Asiimov (Field-Tested)")

        assertEquals(317.14, price)
    }

    @Test
    fun `getSteamPrice sends the Steam session cookie when the user is connected`() = runBlocking {
        val cookies = "sessionid=abc; steamLoginSecure=765611980000%7C%7Ctok"
        val steamApi = mock<SteamApiService>()
        whenever(steamApi.getMarketPriceOverview(marketHashName = "AK-47 | Redline (Field-Tested)", userAgent = "test-ua", sessionCookie = cookies))
            .thenReturn(MarketPriceOverviewResponse(success = true, lowest_price = "\$32.39", median_price = null, volume = null))
        val repository = SkinRepository(mock(), steamApi, connectedSession(cookies))

        val price = repository.getSteamPrice("AK-47 | Redline (Field-Tested)")

        // The stub only matches when the cookie argument arrives — a null cookie
        // would return Mockito's default and fail this assertion.
        assertEquals(32.39, price)
    }

    @Test
    fun `getSteamPrice returns null when Steam reports the item as not listed`() = runBlocking {
        val steamApi = mock<SteamApiService>()
        whenever(steamApi.getMarketPriceOverview(url = any(), appId = any(), currency = any(), marketHashName = any(), userAgent = any(), sessionCookie = anyOrNull()))
            .thenReturn(MarketPriceOverviewResponse(success = false, lowest_price = null, median_price = null, volume = null))
        val repository = SkinRepository(mock(), steamApi, anonymousSession())

        val price = repository.getSteamPrice("Some Unlisted Skin")

        assertEquals(null, price)
    }

    @Test
    fun `getSteamPrice returns null instead of throwing when the call fails`() = runBlocking {
        val steamApi = mock<SteamApiService>()
        whenever(steamApi.getMarketPriceOverview(url = any(), appId = any(), currency = any(), marketHashName = any(), userAgent = any(), sessionCookie = anyOrNull()))
            .thenThrow(RuntimeException("timeout"))
        val repository = SkinRepository(mock(), steamApi, anonymousSession())

        val price = repository.getSteamPrice("AK-47 | Redline (Field-Tested)")

        assertEquals(null, price)
    }
}
