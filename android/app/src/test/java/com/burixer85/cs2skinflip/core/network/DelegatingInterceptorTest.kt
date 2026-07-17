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

class DelegatingInterceptorTest {

    private val request = Request.Builder().url("https://steamcommunity.com/").build()

    @Test
    fun `proceeds on the chain while no delegate is set`() {
        val chain = mock<Interceptor.Chain>()
        val response = mock<Response>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response)

        val result = DelegatingInterceptor().intercept(chain)

        assertSame(response, result)
        verify(chain).proceed(request)
    }

    @Test
    fun `hands the chain to the delegate once one is set`() {
        val chain = mock<Interceptor.Chain>()
        val response = mock<Response>()
        val delegate = mock<Interceptor>()
        whenever(delegate.intercept(chain)).thenReturn(response)

        val interceptor = DelegatingInterceptor()
        interceptor.delegate = delegate
        val result = interceptor.intercept(chain)

        assertSame(response, result)
        verify(delegate).intercept(chain)
        verify(chain, never()).proceed(request)
    }
}
