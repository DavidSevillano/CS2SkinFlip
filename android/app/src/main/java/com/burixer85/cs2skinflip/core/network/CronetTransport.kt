package com.burixer85.cs2skinflip.core.network

import android.content.Context
import android.util.Log
import com.google.android.gms.net.CronetProviderInstaller
import com.google.net.cronet.okhttptransport.CronetInterceptor
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import org.chromium.net.CronetEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards each call to [delegate] when one is set, otherwise proceeds on the
 * plain OkHttp chain. Lets a client switch transports after it has been built.
 */
class DelegatingInterceptor(@Volatile var delegate: Interceptor? = null) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        delegate?.intercept(chain) ?: chain.proceed(chain.request())
}

/**
 * Routes Steam calls through Cronet — Chromium's network stack, shipped by
 * Play services — instead of OkHttp.
 *
 * Steam's market endpoints (priceoverview) reject OkHttp's TLS fingerprint
 * with 429s regardless of headers, IP, or request volume (observed 5/6 blocked
 * from residential and mobile IPs while curl and browsers pass from the same
 * IPs). Cronet handshakes like Chrome itself, which that filter must let
 * through. Play services delivers Cronet asynchronously; until it is ready —
 * or forever, on devices without Play services — calls fall back to plain
 * OkHttp, which at worst reproduces today's behavior.
 */
@Singleton
class CronetTransport @Inject constructor(@ApplicationContext context: Context) {

    val interceptor = DelegatingInterceptor()

    init {
        CronetProviderInstaller.installProvider(context)
            .addOnSuccessListener {
                runCatching {
                    val engine = CronetEngine.Builder(context).build()
                    interceptor.delegate = CronetInterceptor.newBuilder(engine).build()
                }.onFailure { e ->
                    Log.w(TAG, "Cronet engine init failed, Steam calls stay on OkHttp", e)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Cronet unavailable (no Play services?), Steam calls stay on OkHttp", e)
            }
    }

    private companion object {
        const val TAG = "CronetTransport"
    }
}
