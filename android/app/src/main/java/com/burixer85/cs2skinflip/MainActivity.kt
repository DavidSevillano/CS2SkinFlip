package com.burixer85.cs2skinflip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.burixer85.cs2skinflip.core.ads.AdsManager
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.repository.PremiumStatusRepository
import com.burixer85.cs2skinflip.core.preferences.LocaleHelper
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import com.burixer85.cs2skinflip.core.review.ReviewFlowManager
import com.burixer85.cs2skinflip.core.review.ReviewTrigger
import com.burixer85.cs2skinflip.core.ui.theme.Background
import com.burixer85.cs2skinflip.core.ui.theme.CS2SkinFlipTheme
import com.burixer85.cs2skinflip.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var adsManager: AdsManager

    @Inject
    lateinit var premiumStatusRepository: PremiumStatusRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var reviewFlowManager: ReviewFlowManager

    /** Skin ID coming from a push notification tap — triggers navigation in AppNavigation */
    private var notificationSkinId by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        val openedFromAlertNotification = handleNotificationIntent(intent)

        adsManager.initialize(this)
        lifecycleScope.launch {
            if (authRepository.isLoggedIn.first()) premiumStatusRepository.refresh()
        }

        setContent {
            CS2SkinFlipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    AppNavigation(
                        initialSkinId = notificationSkinId,
                        onNavigatedToSkin = { notificationSkinId = null },
                    )
                }
            }
        }

        lifecycleScope.launch {
            val sessionCount = userPreferences.incrementSessionCount()
            if (openedFromAlertNotification) {
                reviewFlowManager.maybeRequestReview(this@MainActivity, ReviewTrigger.ALERT_NOTIFICATION_OPENED)
            } else if (sessionCount in 3..5) {
                reviewFlowManager.maybeRequestReview(this@MainActivity, ReviewTrigger.SESSION_MILESTONE)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "cs2skinflip" && data.host == "auth" && data.path == "/callback") {
            val token = data.getQueryParameter("token") ?: return
            lifecycleScope.launch {
                authRepository.handleCallback(token)
                premiumStatusRepository.refresh()
            }
        }
    }

    /** Returns true if [intent] came from an alert push notification (carries a skinId extra). */
    private fun handleNotificationIntent(intent: Intent): Boolean {
        val skinId = intent.getStringExtra("skinId") ?: return false
        notificationSkinId = skinId
        return true
    }
}
