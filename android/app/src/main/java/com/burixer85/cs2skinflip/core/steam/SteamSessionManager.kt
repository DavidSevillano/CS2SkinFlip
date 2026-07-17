package com.burixer85.cs2skinflip.core.steam

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access to the user's optional Steam Community web session.
 *
 * Steam load-sheds anonymous market requests per IP block at peak hours, while
 * requests carrying a logged-in session's cookies keep working (verified
 * 2026-07-17: the Steam app's cookied call returned 200 from the same LTE
 * network and minute where anonymous Cronet calls got 429). Users can opt in
 * from Settings by signing in to steamcommunity.com inside [SteamLoginScreen];
 * the WebView's cookie jar then holds `steamLoginSecure`, which this class
 * reads and hands to the price call — and nothing else. Cookies never leave
 * the device except toward steamcommunity.com itself, exactly as in a browser.
 *
 * The cookie jar is reached through [readCookies]/[writeCookie] so unit tests
 * can substitute fakes — android.webkit.CookieManager is a static Android
 * service unavailable on the JVM.
 */
@Singleton
class SteamSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    internal var readCookies: (String) -> String? =
        { url -> CookieManager.getInstance().getCookie(url) }
    internal var userAgentProvider: () -> String =
        { WebSettings.getDefaultUserAgent(context) }
    internal var writeCookie: (String, String) -> Unit =
        { url, cookie -> CookieManager.getInstance().setCookie(url, cookie) }
    internal var flushCookies: () -> Unit = { CookieManager.getInstance().flush() }

    /** Forces the WebView cookie jar to disk so the session survives process death. */
    fun persistSession() = flushCookies()

    /** Cookie header for Steam Community requests, or null when not signed in. */
    fun sessionCookieHeader(): String? =
        readCookies(STEAM_COMMUNITY_URL)?.takeIf { it.contains(LOGIN_COOKIE) }

    /**
     * The device WebView's real User-Agent — the identity the session cookies
     * were minted under during [SteamLoginScreen]'s login. Steam scores request
     * coherence, so price calls must present the same UA as the browser that
     * owns the session, not an invented one.
     */
    val browserUserAgent: String by lazy { userAgentProvider() }

    fun isConnected(): Boolean = sessionCookieHeader() != null

    /**
     * Signs the session out locally by expiring Steam's auth cookies in the
     * WebView jar. CookieManager has no per-domain removal, and removeAllCookies
     * would also wipe unrelated WebView state (e.g. ad WebViews).
     */
    fun disconnect() {
        val expired = "; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
        listOf(LOGIN_COOKIE, "sessionid", "steamCountry").forEach { name ->
            writeCookie(STEAM_COMMUNITY_URL, "$name=$expired")
        }
    }

    private companion object {
        const val STEAM_COMMUNITY_URL = "https://steamcommunity.com"
        const val LOGIN_COOKIE = "steamLoginSecure"
    }
}
