package com.burixer85.cs2skinflip.core.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play product ID for the one-time "unlimited alerts" unlock.
 * Must match both the Play Console in-app product listing and the backend's PREMIUM_PRODUCT_ID env var.
 */
const val PREMIUM_PRODUCT_ID = "premium_unlimited_alerts"

/** Thin wrapper around Play Billing — connection handling, purchase launch, and purchase queries. */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
    val purchaseUpdates: SharedFlow<List<Purchase>> = _purchaseUpdates

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                _purchaseUpdates.tryEmit(purchases)
            }
        })
        .enablePendingPurchases()
        .build()

    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    /** Launches the Play Billing purchase sheet for the premium unlock. No-op if billing isn't reachable. */
    suspend fun launchPurchase(activity: Activity, obfuscatedAccountId: String) {
        if (!ensureConnected()) return

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val queryParams = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        val details = client.queryProductDetails(queryParams).productDetailsList?.firstOrNull() ?: return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedAccountId)
            .build()

        client.launchBillingFlow(activity, flowParams)
    }

    /**
     * Re-checks purchases Play Billing has on record. Used as a safety net for the case where
     * the app died right after payment, before the backend could be told about it.
     */
    suspend fun queryOwnedPurchases(): List<Purchase> {
        if (!ensureConnected()) return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        return client.queryPurchasesAsync(params).purchasesList
    }
}

/** Unwraps a (possibly wrapped) Context to find the underlying Activity, needed to launch the billing flow. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
