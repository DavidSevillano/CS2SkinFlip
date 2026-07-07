package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for whether the signed-in user currently sees ads.
 * Backed by `GET /auth/me`, refreshed on app start, after login, and after a
 * confirmed premium purchase. Falls back to the last known value (or `false`)
 * on network failure — ad-gating must fail open to "show ads", never crash.
 */
@Singleton
class PremiumStatusRepository @Inject constructor(
    private val backendApi: CS2BackendApiService,
) {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    suspend fun refresh() {
        runCatching { backendApi.getMe().isPremium }
            .onSuccess { _isPremium.value = it }
    }

    /** Clears cached premium status on logout, so it doesn't leak into the next signed-in session. */
    fun reset() {
        _isPremium.value = false
    }
}
