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
        // Stale/invalid token (e.g. points to a deleted user) — clear it so the UI
        // falls back to the "sign in with Steam" prompt instead of silently failing.
        if (response.code == 401 && token != null) {
            runBlocking { tokenDataStore.clearToken() }
        }
        return response
    }
}
