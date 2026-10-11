package com.burixer85.cs2skinflip.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.ExchangeLoginCodeRequest
import com.burixer85.cs2skinflip.core.data.remote.ExchangeLoginCodeResponse
import com.burixer85.cs2skinflip.core.data.remote.FcmTokenRequest
import com.burixer85.cs2skinflip.core.data.repository.PremiumStatusRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class AuthRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val backendApi: CS2BackendApiService = mock()

    private fun repository(): Pair<AuthRepository, TokenDataStore> {
        val store = TokenDataStore(
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("auth.preferences_pb") })
        )
        val repo = AuthRepository(mock(), store, backendApi, PremiumStatusRepository(backendApi)).apply {
            fcmTokenProvider = { null }
            localeProvider = { "es" }
        }
        return repo to store
    }

    @Test
    fun `beginLogin returns the challenge of the verifier it later sends`() = runTest {
        whenever(backendApi.exchangeLoginCode(any())).thenReturn(ExchangeLoginCodeResponse("jwt"))
        val (repo, _) = repository()

        val challenge = repo.beginLogin()
        repo.completeLogin("code-1")

        val sent = argumentCaptor<ExchangeLoginCodeRequest>()
        verify(backendApi).exchangeLoginCode(sent.capture())
        assertEquals("code-1", sent.firstValue.code)
        assertEquals(challenge, Pkce.challengeFor(sent.firstValue.verifier))
    }

    @Test
    fun `a redeemed code signs the user in`() = runTest {
        whenever(backendApi.exchangeLoginCode(any())).thenReturn(ExchangeLoginCodeResponse("jwt"))
        val (repo, store) = repository()

        repo.beginLogin()
        val ok = repo.completeLogin("code-1")

        assertTrue(ok)
        assertEquals("jwt", store.token.first())
    }

    // A login link the app didn't start — e.g. one an attacker crafted to sign
    // the victim in to the attacker's account — has no verifier to go with it.
    @Test
    fun `a code arriving without a sign-in in progress is refused`() = runTest {
        val (repo, store) = repository()

        val ok = repo.completeLogin("planted-code")

        assertFalse(ok)
        assertNull(store.token.first())
        verify(backendApi, never()).exchangeLoginCode(any())
    }

    @Test
    fun `a verifier is used for one sign-in only`() = runTest {
        whenever(backendApi.exchangeLoginCode(any())).thenReturn(ExchangeLoginCodeResponse("jwt"))
        val (repo, _) = repository()

        repo.beginLogin()
        repo.completeLogin("code-1")
        val second = repo.completeLogin("code-2")

        assertFalse(second)
    }

    @Test
    fun `a failed exchange leaves the user signed out`() = runTest {
        whenever(backendApi.exchangeLoginCode(any())).thenAnswer { throw IOException("offline") }
        val (repo, store) = repository()

        repo.beginLogin()
        val ok = repo.completeLogin("code-1")

        assertFalse(ok)
        assertNull(store.token.first())
    }

    @Test
    fun `the FCM token is sent with the app language`() = runTest {
        val (repo, store) = repository()
        repo.fcmTokenProvider = { "device-token" }
        store.saveToken("jwt")

        repo.resyncFcmToken()

        verify(backendApi).updateFcmToken(FcmTokenRequest("device-token", "es"))
    }

    @Test
    fun `resync does nothing while signed out`() = runTest {
        val (repo, _) = repository()
        repo.fcmTokenProvider = { "device-token" }

        repo.resyncFcmToken()

        verify(backendApi, never()).updateFcmToken(any())
    }
}
