package com.burixer85.cs2skinflip.core.ads

import android.app.Activity
import android.content.Context
import com.burixer85.cs2skinflip.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the AdMob SDK: UMP consent, initialization, and interstitial preloading
 * for the whole app. Banner ads are rendered directly by [BannerAdView] and
 * only need an ad unit ID from here, no shared state.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var interstitialAd: InterstitialAd? = null
    private var skinDetailVisitCount = 0
    private var lastInterstitialShownAt: Long? = null
    private var adsInitialized = false

    fun bannerAdUnitId(): String =
        if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else BuildConfig.ADMOB_BANNER_UNIT_ID

    private fun interstitialAdUnitId(): String =
        if (BuildConfig.DEBUG) TEST_INTERSTITIAL_AD_UNIT_ID else BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID

    /** Call once from `MainActivity.onCreate`. Resolves UMP consent, then initializes the SDK and preloads the first interstitial. */
    fun initialize(activity: Activity) {
        if (adsInitialized) return

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInformation.canRequestAds()) {
                        startMobileAdsAndPreload()
                    }
                }
            },
            {
                // Consent info update failed — don't force ads on, but a previous session may
                // already have obtained consent, in which case there's no reason to block.
                if (consentInformation.canRequestAds()) {
                    startMobileAdsAndPreload()
                }
            },
        )

        // Warm start: if consent was already obtained in a previous session, start loading
        // immediately rather than waiting for the async round trip above.
        if (consentInformation.canRequestAds()) {
            startMobileAdsAndPreload()
        }
    }

    private fun startMobileAdsAndPreload() {
        if (adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(context) {}
        loadInterstitial()
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            context,
            interstitialAdUnitId(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            },
        )
    }

    fun onSkinDetailEntered() {
        skinDetailVisitCount++
    }

    /** Call when leaving Skin Detail. Shows the preloaded interstitial if the frequency/cooldown rule allows it; silently does nothing if none is preloaded yet. */
    fun maybeShowInterstitialOnExit(activity: Activity, isPremium: Boolean) {
        val now = System.currentTimeMillis()
        if (!shouldShowInterstitial(skinDetailVisitCount, lastInterstitialShownAt, now, isPremium)) return
        val ad = interstitialAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial()
            }
        }
        ad.show(activity)
        lastInterstitialShownAt = now
    }

    companion object {
        // Google's official public test ad unit IDs — https://developers.google.com/admob/android/test-ads
        private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        private const val VISIT_INTERVAL = 4
        private const val COOLDOWN_MS = 5 * 60 * 1000L

        /**
         * Pure decision for whether the Skin Detail exit interstitial should fire.
         * Fires every [VISIT_INTERVAL]th visit, but never twice inside [COOLDOWN_MS],
         * and never for premium users.
         */
        fun shouldShowInterstitial(
            visitCount: Int,
            lastShownAt: Long?,
            now: Long,
            isPremium: Boolean,
        ): Boolean {
            if (isPremium) return false
            if (visitCount == 0 || visitCount % VISIT_INTERVAL != 0) return false
            if (lastShownAt != null && now - lastShownAt < COOLDOWN_MS) return false
            return true
        }
    }
}
