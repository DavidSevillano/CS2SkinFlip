package com.burixer85.cs2skinflip.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636, S256) for the Steam sign-in. The app keeps the verifier and
 * only ever sends its challenge; the backend binds the one-time login code to
 * that challenge, so a code read off the redirect is useless to anyone else.
 */
object Pkce {
    private val base64Url = Base64.getUrlEncoder().withoutPadding()
    private val random = SecureRandom()

    /** 32 random bytes → 43 base64url chars, the RFC's recommended size. */
    fun newVerifier(): String = ByteArray(32).also(random::nextBytes).let(base64Url::encodeToString)

    fun challengeFor(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
