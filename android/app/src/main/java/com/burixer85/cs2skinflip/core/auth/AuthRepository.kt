package com.burixer85.cs2skinflip.core.auth

import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.ExchangeLoginCodeRequest
import com.burixer85.cs2skinflip.core.data.remote.FcmTokenRequest
import com.burixer85.cs2skinflip.core.data.repository.PremiumStatusRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
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

    /** The PKCE verifier of the sign-in in progress, if any. Never leaves the device. */
    @Volatile
    private var pendingVerifier: String? = null

    /** Device FCM token lookup, swappable in tests (FirebaseMessaging is unavailable on the JVM). */
    internal var fcmTokenProvider: suspend () -> String? = {
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /**
     * Starts a Steam sign-in and returns the PKCE challenge to send to
     * `GET /auth/steam`. Starting again discards any earlier attempt.
     */
    fun beginLogin(): String {
        val verifier = Pkce.newVerifier()
        pendingVerifier = verifier
        return Pkce.challengeFor(verifier)
    }

    /**
     * Redeems the one-time code the backend put on the sign-in redirect. Returns
     * false — and stays signed out — when no sign-in was started here (a login
     * link the app didn't ask for, e.g. one planted to sign the user in to
     * someone else's account) or when the backend refuses the code.
     */
    suspend fun completeLogin(code: String): Boolean {
        val verifier = pendingVerifier ?: return false
        pendingVerifier = null
        val token = try {
            backendApi.exchangeLoginCode(ExchangeLoginCodeRequest(code, verifier)).token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        tokenDataStore.saveToken(token)
        syncFcmToken()
        return true
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
        premiumStatusRepository.reset()
    }

    /** Permanently deletes the caller's account and all associated data on the backend. */
    suspend fun deleteAccount() {
        backendApi.deleteAccount()
        tokenDataStore.clearToken()
        premiumStatusRepository.reset()
    }

    /** Register or refresh the FCM token on the backend. No-op if not logged in. */
    suspend fun updateFcmToken(token: String) {
        runCatching { backendApi.updateFcmToken(FcmTokenRequest(token)) }
        // Silently ignore failures — token update is best-effort
    }

    /**
     * Sends the device's current FCM token right after sign-in.
     * Needed because [onNewToken][com.google.firebase.messaging.FirebaseMessagingService.onNewToken]
     * only fires once per install (or on token rotation) — usually before any user is signed in —
     * so without this, `fcmToken` would never get associated with the account.
     */
    private suspend fun syncFcmToken() {
        fcmTokenProvider()?.let { updateFcmToken(it) }
    }
}
