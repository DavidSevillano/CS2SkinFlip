package com.burixer85.cs2skinflip.core.data.repository

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.billing.BillingManager
import com.burixer85.cs2skinflip.core.billing.PREMIUM_PRODUCT_ID
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.VerifyPurchaseRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    private val billingManager: BillingManager,
    private val backendApi: CS2BackendApiService,
    private val premiumStatusRepository: PremiumStatusRepository,
) {
    /** Raw purchase results from Play Billing — collect and pass each purchase to [verify]. */
    val purchaseUpdates: SharedFlow<List<Purchase>> = billingManager.purchaseUpdates

    private val _isVerifying = MutableStateFlow(false)
    /** True while a purchase is being verified against the backend — lets the UI show a spinner instead of briefly looking like the purchase failed. */
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    /**
     * Launches the Play Billing purchase sheet. Attaches the signed-in user's ID so the backend
     * can reject the token if replayed against a different account.
     * Returns false (without launching) if the user's ID couldn't be fetched — e.g. no network.
     */
    suspend fun purchasePremium(activity: Activity): Boolean {
        val userId = runCatching { backendApi.getMe().id }.getOrNull() ?: return false
        billingManager.launchPurchase(activity, obfuscatedAccountId = userId)
        return true
    }

    /** Verifies one purchase with the backend. Returns true if premium is now unlocked. */
    suspend fun verify(purchase: Purchase): Boolean {
        if (PREMIUM_PRODUCT_ID !in purchase.products) return false
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        _isVerifying.value = true
        val unlocked = try {
            runCatching {
                backendApi.verifyPurchase(VerifyPurchaseRequest(purchase.purchaseToken)).isPremium
            }.getOrDefault(false)
        } finally {
            _isVerifying.value = false
        }
        if (unlocked) premiumStatusRepository.refresh()
        return unlocked
    }

    /** Safety net for purchases Play Billing has on record but the backend never confirmed — call on app start / login. */
    suspend fun syncPendingPurchases(): Boolean {
        var unlocked = false
        billingManager.queryOwnedPurchases().forEach { if (verify(it)) unlocked = true }
        return unlocked
    }
}
