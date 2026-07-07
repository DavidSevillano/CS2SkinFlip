package com.burixer85.cs2skinflip.core.data.repository

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.billing.BillingManager
import com.burixer85.cs2skinflip.core.billing.PREMIUM_PRODUCT_ID
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.VerifyPurchaseRequest
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    private val billingManager: BillingManager,
    private val backendApi: CS2BackendApiService,
) {
    /** Raw purchase results from Play Billing — collect and pass each purchase to [verify]. */
    val purchaseUpdates: SharedFlow<List<Purchase>> = billingManager.purchaseUpdates

    /** Launches the Play Billing purchase sheet. Attaches the signed-in user's ID so the backend can reject the token if replayed against a different account. */
    suspend fun purchasePremium(activity: Activity) {
        val userId = runCatching { backendApi.getMe().id }.getOrNull() ?: return
        billingManager.launchPurchase(activity, obfuscatedAccountId = userId)
    }

    /** Verifies one purchase with the backend. Returns true if premium is now unlocked. */
    suspend fun verify(purchase: Purchase): Boolean {
        if (PREMIUM_PRODUCT_ID !in purchase.products) return false
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        return runCatching {
            backendApi.verifyPurchase(VerifyPurchaseRequest(purchase.purchaseToken)).isPremium
        }.getOrDefault(false)
    }

    /** Safety net for purchases Play Billing has on record but the backend never confirmed — call on app start / login. */
    suspend fun syncPendingPurchases(): Boolean {
        var unlocked = false
        billingManager.queryOwnedPurchases().forEach { if (verify(it)) unlocked = true }
        return unlocked
    }
}
