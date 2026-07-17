package com.burixer85.cs2skinflip.features.settings

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.steam.SteamSessionManager
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SteamLoginViewModel @Inject constructor(
    private val steamSession: SteamSessionManager,
) : ViewModel() {
    fun hasSession(): Boolean = steamSession.isConnected()
    fun persistSession() = steamSession.persistSession()
}

/**
 * Hosts Steam's own login page in a WebView so the user's Steam Community
 * session ends up in the app's cookie jar, from where [SteamSessionManager]
 * attaches it to price requests. The WebView is deliberately dumb: top-level
 * navigation is locked to Steam's domains, no JavaScript bridge is installed,
 * and nothing reads page content — only the cookie jar is observed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SteamLoginScreen(
    onBack: () -> Unit,
    viewModel: SteamLoginViewModel = hiltViewModel(),
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val finished = remember { mutableStateOf(false) }

    fun finishIfLoggedIn() {
        if (!finished.value && viewModel.hasSession()) {
            finished.value = true
            viewModel.persistSession()
            onBack()
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
                            val host = request?.url?.host ?: return false
                            val steamHost = host == "steamcommunity.com" ||
                                host.endsWith(".steamcommunity.com") ||
                                host == "steampowered.com" ||
                                host.endsWith(".steampowered.com")
                            // Anything leaving Steam is swallowed rather than followed.
                            return !steamHost
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            finishIfLoggedIn()
                        }
                    }
                    webViewRef.value = this
                    loadUrl("https://steamcommunity.com/login/home/")
                }
            },
        )
    }
}
