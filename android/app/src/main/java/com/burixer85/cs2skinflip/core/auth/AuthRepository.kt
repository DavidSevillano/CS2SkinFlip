package com.burixer85.cs2skinflip.core.auth

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.FcmTokenRequest
import com.burixer85.cs2skinflip.core.data.remote.LoginRequest
import com.burixer85.cs2skinflip.core.data.remote.RegisterRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    private val backendApi: CS2BackendApiService,
) {
    val isLoggedIn: Flow<Boolean> = tokenDataStore.token.map { it != null }
    val token: Flow<String?> = tokenDataStore.token

    /** Steam OAuth deep-link callback — saves the JWT */
    suspend fun handleCallback(token: String) {
        tokenDataStore.saveToken(token)
    }

    /** Email/password register — returns null on success, error message on failure */
    suspend fun register(email: String, password: String, username: String?): String? {
        return runCatching {
            val response = backendApi.register(RegisterRequest(email, password, username))
            tokenDataStore.saveToken(response.token)
        }.fold(
            onSuccess = { null },
            onFailure = { e -> humanError(e, default = "Registration failed") },
        )
    }

    /** Email/password login — returns null on success, error message on failure */
    suspend fun login(email: String, password: String): String? {
        return runCatching {
            val response = backendApi.login(LoginRequest(email, password))
            tokenDataStore.saveToken(response.token)
        }.fold(
            onSuccess = { null },
            onFailure = { e -> humanError(e, default = "Login failed") },
        )
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
    }

    /** Register or refresh the FCM token on the backend. No-op if not logged in. */
    suspend fun updateFcmToken(token: String) {
        runCatching { backendApi.updateFcmToken(FcmTokenRequest(token)) }
        // Silently ignore failures — token update is best-effort
    }

    private fun humanError(e: Throwable, default: String): String {
        if (e is HttpException) {
            return when (e.code()) {
                401 -> "Invalid email or password"
                409 -> "That email is already registered"
                400 -> "Check your email and password (min. 8 characters)"
                else -> default
            }
        }
        return e.message ?: default
    }
}
