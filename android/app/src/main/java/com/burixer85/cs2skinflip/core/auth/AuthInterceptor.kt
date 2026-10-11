package com.burixer85.cs2skinflip.core.auth

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenDataStore: TokenDataStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenDataStore.token.firstOrNull() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (token == null) return response

        if (response.code == 401) {
            // Stale/invalid token (e.g. points to a deleted user) — clear it so the UI
            // falls back to the "sign in with Steam" prompt instead of silently failing.
            runBlocking { tokenDataStore.clearToken() }
        } else {
            // Sliding renewal: the backend sends a fresh JWT when ours nears expiry.
            // Swapped in only if ours is still the stored one, so a sign-out that
            // happened while this request was in flight stays signed out.
            response.header(REFRESHED_TOKEN_HEADER)?.let { fresh ->
                runBlocking { tokenDataStore.replaceToken(expected = token, replacement = fresh) }
            }
        }
        return response
    }

    private companion object {
        const val REFRESHED_TOKEN_HEADER = "X-Refreshed-Token"
    }
}
