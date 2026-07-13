package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.SteamPriceDto
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SkinRepositoryTest {

    @Test
    fun `getTrendingSkins propagates the failure instead of returning mock data`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getTopMovers("rising")).thenThrow(RuntimeException("no network"))
        val repository = SkinRepository(backendApi)

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
        val repository = SkinRepository(backendApi)

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
        val repository = SkinRepository(backendApi)

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
        val repository = SkinRepository(backendApi)

        var threw = false
        try {
            repository.getAllWeapons()
        } catch (e: RuntimeException) {
            threw = true
        }

        assertTrue(threw)
    }

    @Test
    fun `getSteamPrice returns the price on success`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getSteamPrice("ak-47-redline")).thenReturn(SteamPriceDto(price = 32.39))
        val repository = SkinRepository(backendApi)

        val price = repository.getSteamPrice("ak-47-redline")

        assertEquals(32.39, price)
    }

    @Test
    fun `getSteamPrice returns null instead of throwing when the call fails`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getSteamPrice("ak-47-redline")).thenThrow(RuntimeException("timeout"))
        val repository = SkinRepository(backendApi)

        val price = repository.getSteamPrice("ak-47-redline")

        assertEquals(null, price)
    }
}
