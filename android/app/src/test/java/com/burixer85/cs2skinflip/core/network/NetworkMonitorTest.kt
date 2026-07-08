package com.burixer85.cs2skinflip.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetworkMonitorTest {

    private fun contextWith(connectivityManager: ConnectivityManager): Context {
        val context = mock<Context>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        return context
    }

    // NetworkMonitor's init block builds a real android.net.NetworkRequest via NetworkRequest.Builder,
    // which is an unmocked Android SDK stub under plain JUnit (no Robolectric) and throws/NPEs if
    // constructed for real. Mocking its construction keeps this a pure-logic-seam test.
    private fun withMockedNetworkRequestBuilder(block: () -> Unit) {
        Mockito.mockConstruction(NetworkRequest.Builder::class.java) { mockBuilder, _ ->
            whenever(mockBuilder.addCapability(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.build()).thenReturn(mock())
        }.use {
            block()
        }
    }

    @Test
    fun `isOnline is true at construction when the active network is validated`() {
        val network = mock<Network>()
        val capabilities = mock<NetworkCapabilities>()
        whenever(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        val connectivityManager = mock<ConnectivityManager>()
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities)

        withMockedNetworkRequestBuilder {
            val monitor = NetworkMonitor(contextWith(connectivityManager))

            assertTrue(monitor.isOnline.value)
        }
    }

    @Test
    fun `isOnline is false at construction when there is no active network`() {
        val connectivityManager = mock<ConnectivityManager>()
        whenever(connectivityManager.activeNetwork).thenReturn(null)

        withMockedNetworkRequestBuilder {
            val monitor = NetworkMonitor(contextWith(connectivityManager))

            assertFalse(monitor.isOnline.value)
        }
    }
}
