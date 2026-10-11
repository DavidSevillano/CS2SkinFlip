package com.burixer85.cs2skinflip.core.data.repository

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.billing.BillingManager
import com.burixer85.cs2skinflip.core.billing.PREMIUM_PRODUCT_ID
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.VerifyPurchaseRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Why premium just became active — only a fresh purchase gets the celebration dialog. */
enum class PremiumUnlockSource { PURCHASE, RESTORED }

@Singleton
class BillingRepository @Inject constructor(
    private val billingManager: BillingManager,
    private val backendApi: CS2BackendApiService,
    private val premiumStatusRepository: PremiumStatusRepository,
) {
    private val _isVerifying = MutableStateFlow(false)
    /** True while a purchase is being verified against the backend — lets the UI show a spinner instead of briefly looking like the purchase failed. */
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _premiumUnlocked = MutableSharedFlow<PremiumUnlockSource>(extraBufferCapacity = 8)
    /** Emits whenever a verification unlocks premium; screens reload on it. */
    val premiumUnlocked: SharedFlow<PremiumUnlockSource> = _premiumUnlocked.asSharedFlow()

    // Tokens the backend already confirmed in this process. Several screens ask
    // for the pending-purchase check on sign-in; each token reaches the backend
    // (and Google Play behind it) once per launch rather than once per screen.
    private val verifiedTokens: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val syncMutex = Mutex()

    /**
     * Starts the app's single consumer of Play Billing results. Call once per
     * process (CS2SkinFlipApp). Each screen used to collect these itself, so a
     * purchase made with two of them alive was verified twice.
     */
    fun observePurchaseUpdates(scope: CoroutineScope) {
        scope.launch {
            billingManager.purchaseUpdates.collect { purchases ->
                // Verify every purchase — `any { verify(it) }` would stop at the first.
                if (purchases.map { verify(it) }.any { it }) _premiumUnlocked.emit(PremiumUnlockSource.PURCHASE)
            }
        }
    }

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
        if (unlocked) {
            verifiedTokens += purchase.purchaseToken
            premiumStatusRepository.refresh()
        }
        return unlocked
    }

    /**
     * Safety net for purchases Play Billing has on record but the backend never confirmed — call on
     * app start / login. Also how purchases made before the backend recorded them get recorded, which
     * refund revocation depends on, so it re-checks each owned token once per launch.
     */
    suspend fun syncPendingPurchases(): Boolean = syncMutex.withLock {
        val pending = billingManager.queryOwnedPurchases().filter { it.purchaseToken !in verifiedTokens }
        val unlocked = pending.map { verify(it) }.any { it }
        if (unlocked) _premiumUnlocked.emit(PremiumUnlockSource.RESTORED)
        unlocked
    }
}
