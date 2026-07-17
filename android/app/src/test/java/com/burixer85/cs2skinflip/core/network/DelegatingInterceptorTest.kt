package com.burixer85.cs2skinflip.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch

class DelegatingInterceptorTest {

    private val request = Request.Builder().url("https://steamcommunity.com/").build()

    private fun mockChain(response: Response): Interceptor.Chain {
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response)
        return chain
    }

    @Test
    fun `proceeds on the chain while no delegate is set`() {
        val response = mock<Response>()
        val chain = mockChain(response)

        val result = DelegatingInterceptor(log = {}).intercept(chain)

        assertSame(response, result)
        verify(chain).proceed(request)
    }

    @Test
    fun `waits for initialization and then uses the delegate that arrived`() {
        val response = mock<Response>()
        val chain = mockChain(mock())
        val delegate = mock<Interceptor>()
        whenever(delegate.intercept(chain)).thenReturn(response)

        val initDone = CountDownLatch(1)
        val interceptor = DelegatingInterceptor(initDone = initDone, initWaitMillis = 2_000, log = {})
        Thread {
            interceptor.delegate = delegate
            initDone.countDown()
        }.start()

        val result = interceptor.intercept(chain)

        assertSame(response, result)
        verify(chain, never()).proceed(request)
    }

    @Test
    fun `falls back to the chain when initialization never finishes in time`() {
        val response = mock<Response>()
        val chain = mockChain(response)

        val interceptor = DelegatingInterceptor(
            initDone = CountDownLatch(1),
            initWaitMillis = 50,
            log = {},
        )
        val result = interceptor.intercept(chain)

        assertSame(response, result)
        verify(chain).proceed(request)
    }

    @Test
    fun `hands the chain to the delegate once one is set`() {
        val response = mock<Response>()
        val chain = mockChain(mock())
        val delegate = mock<Interceptor>()
        whenever(delegate.intercept(chain)).thenReturn(response)

        val interceptor = DelegatingInterceptor(log = {})
        interceptor.delegate = delegate
        val result = interceptor.intercept(chain)

        assertSame(response, result)
        verify(delegate).intercept(chain)
        verify(chain, never()).proceed(request)
    }
}
