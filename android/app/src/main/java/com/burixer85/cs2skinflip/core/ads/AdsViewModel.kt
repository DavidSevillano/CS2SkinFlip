package com.burixer85.cs2skinflip.core.ads

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.burixer85.cs2skinflip.core.data.repository.PremiumStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AdsViewModel @Inject constructor(
    private val premiumStatusRepository: PremiumStatusRepository,
    private val adsManager: AdsManager,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = premiumStatusRepository.isPremium
    val bannerAdUnitId: String get() = adsManager.bannerAdUnitId()

    fun onSkinDetailEntered() = adsManager.onSkinDetailEntered()

    fun maybeShowInterstitialOnExit(activity: Activity) =
        adsManager.maybeShowInterstitialOnExit(activity, isPremium.value)
}
