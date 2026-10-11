package com.burixer85.cs2skinflip.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {

    // Same pair the backend's auth.test.ts pins: both sides must agree on S256.
    @Test
    fun `challenge is base64url sha256 of the verifier, matching the backend`() {
        assertEquals(
            "0sD9xcv3ROe19ThujFtMtauXJzymPhgUDlDgWgv1AL8",
            Pkce.challengeFor("dBjftJeZ4CVP-mJ0K33TzhIDn7RnPd5Q0HOdODN1hEk"),
        )
    }

    @Test
    fun `verifier is 43 unreserved characters, as the backend requires`() {
        val verifier = Pkce.newVerifier()

        assertTrue(verifier, Regex("^[A-Za-z0-9._~-]{43}$").matches(verifier))
    }

    @Test
    fun `every verifier is fresh`() {
        assertNotEquals(Pkce.newVerifier(), Pkce.newVerifier())
    }
}
