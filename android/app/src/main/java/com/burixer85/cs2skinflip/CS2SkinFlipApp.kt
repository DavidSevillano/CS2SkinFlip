package com.burixer85.cs2skinflip

import android.app.Application
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CS2SkinFlipApp : Application() {

    @Inject
    lateinit var billingRepository: BillingRepository

    @Inject
    lateinit var authRepository: AuthRepository

    // Lives as long as the process: app-wide work that must not die with a screen.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        billingRepository.observePurchaseUpdates(appScope)
        ensurePriceAlertChannel(this)
        appScope.launch { authRepository.resyncFcmToken() }
    }
}
