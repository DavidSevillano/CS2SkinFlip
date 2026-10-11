package com.burixer85.cs2skinflip.features.settings

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.burixer85.cs2skinflip.BuildConfig
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.repository.PremiumStatusRepository
import com.burixer85.cs2skinflip.core.steam.SteamSessionManager
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SteamLoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val premiumStatusRepository: PremiumStatusRepository,
    private val steamSession: SteamSessionManager,
) : ViewModel() {
    /** Starts a sign-in and returns the `/auth/steam` URL carrying its PKCE challenge. */
    fun beginSignIn(): String =
        "${BuildConfig.BACKEND_URL.trimEnd('/')}/auth/steam?challenge=${authRepository.beginLogin()}"

    /**
     * Completes the single Steam sign-in: the OpenID round-trip in the WebView
     * has just left a one-time code on the deep-link redirect *and* deposited
     * `steamLoginSecure` in the shared cookie jar. Redeems the code and persists
     * the session so price calls can carry it. False if the code was refused.
     */
    suspend fun completeSignIn(code: String): Boolean {
        if (!authRepository.completeLogin(code)) return false
        steamSession.persistSession()
        premiumStatusRepository.refresh()
        return true
    }
}

/**
 * The app's single Steam sign-in. It hosts the backend's own `/auth/steam`
 * OpenID flow in a WebView so one login produces *both* halves of a Steam
 * identity in one place:
 *
 *  1. Logging in on Steam's page deposits `steamLoginSecure` in the app's cookie
 *     jar, from where [SteamSessionManager] attaches it to price requests (Steam
 *     load-sheds anonymous market calls at peak hours; cookied calls keep working).
 *  2. When the OpenID round-trip finishes, the backend redirects to the app's
 *     `cs2skinflip://auth/callback?code=…` deep link, which this screen
 *     intercepts and redeems with the PKCE verifier only this process holds.
 *     This is the only place a sign-in can complete: the app no longer accepts
 *     that link from outside, so no other app or web page can plant a login.
 *
 * The WebView is deliberately dumb: top-level navigation is locked to our backend
 * and Steam's domains, no JavaScript bridge is installed, and nothing reads page
 * content — only the cookie jar and the final redirect are observed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SteamLoginScreen(
    onBack: () -> Unit,
    viewModel: SteamLoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val finished = remember { mutableStateOf(false) }

    fun completeSignIn(code: String) {
        if (finished.value) return
        finished.value = true
        scope.launch {
            if (viewModel.completeSignIn(code)) {
                Toast.makeText(context, R.string.steam_prices_connected_toast, Toast.LENGTH_SHORT).show()
                onBack()
            } else {
                // Refused or offline: start over with a fresh challenge.
                Toast.makeText(context, R.string.error_generic_retry, Toast.LENGTH_SHORT).show()
                finished.value = false
                webViewRef.value?.loadUrl(viewModel.beginSignIn())
            }
        }
    }

    BackHandler {
        val webView = webViewRef.value
        if (webView?.canGoBack() == true) webView.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Text(
                stringResource(R.string.steam_login_title),
                modifier = Modifier.align(Alignment.Center),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url ?: return false
                            // The backend's final redirect carries a one-time code on
                            // the app's own scheme — capture it and never try to load it.
                            if (url.scheme == DEEP_LINK_SCHEME) {
                                url.getQueryParameter("code")?.let { completeSignIn(it) }
                                return true
                            }
                            val host = url.host ?: return false
                            val allowed = host == BACKEND_HOST ||
                                host == "steamcommunity.com" || host.endsWith(".steamcommunity.com") ||
                                host == "steampowered.com" || host.endsWith(".steampowered.com")
                            // Anything outside our backend + Steam is swallowed rather than followed.
                            return !allowed
                        }
                    }
                    webViewRef.value = this
                    loadUrl(viewModel.beginSignIn())
                }
            },
        )
    }
}

private const val DEEP_LINK_SCHEME = "cs2skinflip"
private val BACKEND_HOST: String? = Uri.parse(BuildConfig.BACKEND_URL).host
