package com.burixer85.cs2skinflip.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn

class AuthInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store() = TokenDataStore(
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("auth.preferences_pb") })
    )

    /** A chain whose server answers [code], optionally with a refreshed token, running [onProceed] first. */
    private fun chain(code: Int = 200, refreshed: String? = null, onProceed: () -> Unit = {}): Interceptor.Chain {
        val request = Request.Builder().url("https://api.example.com/alerts").build()
        return mock {
            on { request() } doReturn request
            on { proceed(any()) } doAnswer { invocation ->
                onProceed()
                Response.Builder()
                    .request(invocation.getArgument(0))
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .apply { refreshed?.let { header("X-Refreshed-Token", it) } }
                    .build()
            }
        }
    }

    @Test
    fun `sends the stored token as a bearer header`() = runBlocking {
        val store = store().apply { saveToken("old") }
        val chain = chain()

        AuthInterceptor(store).intercept(chain)

        val sent = argumentCaptor<Request>()
        verify(chain).proceed(sent.capture())
        assertEquals("Bearer old", sent.firstValue.header("Authorization"))
    }

    @Test
    fun `stores the renewed token the backend hands back`() = runBlocking {
        val store = store().apply { saveToken("old") }

        AuthInterceptor(store).intercept(chain(refreshed = "new"))

        assertEquals("new", store.token.first())
    }

    // Signing out while a request is in flight must not be undone by that
    // request's renewed token arriving afterwards.
    @Test
    fun `a renewal arriving after sign-out does not sign the user back in`() = runBlocking {
        val store = store().apply { saveToken("old") }

        AuthInterceptor(store).intercept(chain(refreshed = "new") { runBlocking { store.clearToken() } })

        assertNull(store.token.first())
    }

    @Test
    fun `clears a token the backend rejects`() = runBlocking {
        val store = store().apply { saveToken("old") }

        AuthInterceptor(store).intercept(chain(code = 401))

        assertNull(store.token.first())
    }
}
