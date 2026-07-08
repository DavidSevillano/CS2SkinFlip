package com.burixer85.cs2skinflip.core.data.repository

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.billing.BillingManager
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
import com.burixer85.cs2skinflip.core.data.remote.VerifyPurchaseResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BillingRepositoryTest {

    private fun meResponse(id: String = "user-1") = MeResponseDto(
        id = id,
        steamId = "76561198000000000",
        username = "tester",
        avatarUrl = null,
        isPremium = false,
        premiumUntil = null,
    )

    private fun purchase(
        products: List<String> = listOf("premium_unlimited_alerts"),
        state: Int = Purchase.PurchaseState.PURCHASED,
        token: String = "token-1",
    ): Purchase {
        val p = mock<Purchase>()
        whenever(p.products).thenReturn(products)
        whenever(p.purchaseState).thenReturn(state)
        whenever(p.purchaseToken).thenReturn(token)
        return p
    }

    @Test
    fun `purchasePremium launches billing with the signed-in user's id`() = runBlocking {
        val billingManager = mock<BillingManager>()
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getMe()).thenReturn(meResponse(id = "user-42"))
        val activity = mock<Activity>()
        val repository = BillingRepository(billingManager, backendApi, mock())

        val result = repository.purchasePremium(activity)

        verify(billingManager).launchPurchase(activity, "user-42")
        assertTrue(result)
    }

    @Test
    fun `purchasePremium does not launch billing when the user's id can't be fetched`() = runBlocking {
        val billingManager = mock<BillingManager>()
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.getMe()).thenThrow(RuntimeException("network error"))
        val repository = BillingRepository(billingManager, backendApi, mock())

        val result = repository.purchasePremium(mock())

        verify(billingManager, never()).launchPurchase(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        assertFalse(result)
    }

    @Test
    fun `verify returns false for a purchase of a different product`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        val repository = BillingRepository(mock(), backendApi, mock())

        val result = repository.verify(purchase(products = listOf("some_other_sku")))

        assertFalse(result)
        verify(backendApi, never()).verifyPurchase(org.mockito.kotlin.any())
        Unit
    }

    @Test
    fun `verify returns false for a purchase that is not in the PURCHASED state`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        val repository = BillingRepository(mock(), backendApi, mock())

        val result = repository.verify(purchase(state = Purchase.PurchaseState.PENDING))

        assertFalse(result)
        verify(backendApi, never()).verifyPurchase(org.mockito.kotlin.any())
        Unit
    }

    @Test
    fun `verify unlocks premium and refreshes status when the backend confirms the purchase`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.verifyPurchase(org.mockito.kotlin.any()))
            .thenReturn(VerifyPurchaseResponseDto(isPremium = true, premiumUntil = null))
        val premiumStatusRepository = mock<PremiumStatusRepository>()
        val repository = BillingRepository(mock(), backendApi, premiumStatusRepository)

        val result = repository.verify(purchase())

        assertTrue(result)
        verify(premiumStatusRepository).refresh()
    }

    @Test
    fun `verify does not refresh status when the backend rejects the purchase`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.verifyPurchase(org.mockito.kotlin.any()))
            .thenReturn(VerifyPurchaseResponseDto(isPremium = false, premiumUntil = null))
        val premiumStatusRepository = mock<PremiumStatusRepository>()
        val repository = BillingRepository(mock(), backendApi, premiumStatusRepository)

        val result = repository.verify(purchase())

        assertFalse(result)
        verify(premiumStatusRepository, never()).refresh()
    }

    @Test
    fun `verify fails safe when the backend call throws`() = runBlocking {
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.verifyPurchase(org.mockito.kotlin.any())).thenThrow(RuntimeException("network error"))
        val premiumStatusRepository = mock<PremiumStatusRepository>()
        val repository = BillingRepository(mock(), backendApi, premiumStatusRepository)

        val result = repository.verify(purchase())

        assertFalse(result)
        verify(premiumStatusRepository, never()).refresh()
    }

    @Test
    fun `syncPendingPurchases verifies every owned purchase and reports if any unlocked premium`() = runBlocking {
        val billingManager = mock<BillingManager>()
        val owned = listOf(purchase(products = listOf("some_other_sku")), purchase(token = "token-2"))
        whenever(billingManager.queryOwnedPurchases()).thenReturn(owned)
        val backendApi = mock<CS2BackendApiService>()
        whenever(backendApi.verifyPurchase(org.mockito.kotlin.any()))
            .thenReturn(VerifyPurchaseResponseDto(isPremium = true, premiumUntil = null))
        val repository = BillingRepository(billingManager, backendApi, mock())

        val result = repository.syncPendingPurchases()

        assertTrue(result)
    }

    @Test
    fun `syncPendingPurchases returns false when no owned purchase unlocks premium`() = runBlocking {
        val billingManager = mock<BillingManager>()
        whenever(billingManager.queryOwnedPurchases()).thenReturn(emptyList())
        val repository = BillingRepository(billingManager, mock(), mock())

        val result = repository.syncPendingPurchases()

        assertFalse(result)
    }
}
