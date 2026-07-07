package com.burixer85.cs2skinflip.core.auth

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.FcmTokenRequest
import com.burixer85.cs2skinflip.core.data.repository.PremiumStatusRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AuthRepository @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    private val backendApi: CS2BackendApiService,
    private val premiumStatusRepository: PremiumStatusRepository,
) {
    val isLoggedIn: Flow<Boolean> = tokenDataStore.token.map { it != null }
    val token: Flow<String?> = tokenDataStore.token

    /** Steam OAuth deep-link callback — saves the JWT */
    suspend fun handleCallback(token: String) {
        tokenDataStore.saveToken(token)
        syncFcmToken()
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
        premiumStatusRepository.reset()
    }

    /** Register or refresh the FCM token on the backend. No-op if not logged in. */
    suspend fun updateFcmToken(token: String) {
        runCatching { backendApi.updateFcmToken(FcmTokenRequest(token)) }
        // Silently ignore failures — token update is best-effort
    }

    /**
     * Sends the device's current FCM token right after the Steam callback.
     * Needed because [onNewToken][com.google.firebase.messaging.FirebaseMessagingService.onNewToken]
     * only fires once per install (or on token rotation) — usually before any user is signed in —
     * so without this, `fcmToken` would never get associated with the account.
     */
    private suspend fun syncFcmToken() {
        val token = suspendCancellableCoroutine<String?> { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        token?.let { updateFcmToken(it) }
    }
}
