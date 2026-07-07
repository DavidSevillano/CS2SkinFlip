package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PremiumStatusRepositoryTest {

    private fun meResponse(isPremium: Boolean) = MeResponseDto(
        id = "user-1",
        steamId = "76561198000000000",
        username = "tester",
        avatarUrl = null,
        isPremium = isPremium,
        premiumUntil = null,
    )

    @Test
    fun `refresh updates isPremium from a successful response`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getMe()).thenReturn(meResponse(isPremium = true))
        val repository = PremiumStatusRepository(backendApi)

        repository.refresh()

        assertEquals(true, repository.isPremium.value)
    }

    @Test
    fun `refresh keeps the last known value when the backend call fails`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getMe())
            .thenReturn(meResponse(isPremium = true))
            .thenThrow(RuntimeException("network error"))
        val repository = PremiumStatusRepository(backendApi)
        repository.refresh() // first call succeeds, isPremium becomes true

        repository.refresh() // second call throws

        assertEquals(true, repository.isPremium.value)
    }

    @Test
    fun `isPremium defaults to false before any refresh`() {
        val backendApi = mock<CS2BackendApiService>()
        val repository = PremiumStatusRepository(backendApi)

        assertEquals(false, repository.isPremium.value)
    }
}
