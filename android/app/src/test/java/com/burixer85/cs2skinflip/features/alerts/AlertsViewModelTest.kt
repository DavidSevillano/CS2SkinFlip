package com.burixer85.cs2skinflip.features.alerts

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.repository.AlertRepository
import com.burixer85.cs2skinflip.core.data.repository.AlertsState
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(
        loggedIn: Boolean = true,
        purchaseUpdates: MutableSharedFlow<List<Purchase>> = MutableSharedFlow(extraBufferCapacity = 1),
        fetchResult: AlertsState = AlertsState(alerts = emptyList(), isPremium = false, activeCount = 0, freeLimit = 1),
        syncPendingPurchases: Boolean = false,
    ): Triple<AlertsViewModel, AlertRepository, BillingRepository> {
        val alertRepository = mock<AlertRepository>()
        val authRepository = mock<AuthRepository>()
        val skinRepository = mock<SkinRepository>()
        val analytics = mock<AnalyticsService>()
        val billingRepository = mock<BillingRepository>()

        whenever(authRepository.isLoggedIn).thenReturn(flowOf(loggedIn))
        whenever(billingRepository.purchaseUpdates).thenReturn(purchaseUpdates)
        whenever(billingRepository.syncPendingPurchases()).thenReturn(syncPendingPurchases)
        whenever(alertRepository.fetchAll()).thenReturn(fetchResult)

        val viewModel = AlertsViewModel(alertRepository, authRepository, skinRepository, analytics, billingRepository)
        return Triple(viewModel, alertRepository, billingRepository)
    }

    @Test
    fun `loads alerts on init when the user is logged in`() = runTest {
        val (viewModel, _, _) = viewModel(loggedIn = true)

        assertTrue(viewModel.uiState.value is AlertsUiState.Success)
    }

    @Test
    fun `shows NotLoggedIn state when the user is not logged in`() = runTest {
        val (viewModel, _, _) = viewModel(loggedIn = false)

        assertEquals(AlertsUiState.NotLoggedIn, viewModel.uiState.value)
    }

    @Test
    fun `reloads alerts when a pending purchase is synced on login`() = runTest {
        val (_, alertRepository, _) = viewModel(loggedIn = true, syncPendingPurchases = true)

        verify(alertRepository, org.mockito.kotlin.times(2)).fetchAll()
    }

    @Test
    fun `does not reload twice when there is no pending purchase to sync`() = runTest {
        val (_, alertRepository, _) = viewModel(loggedIn = true, syncPendingPurchases = false)

        verify(alertRepository, org.mockito.kotlin.times(1)).fetchAll()
    }

    @Test
    fun `purchasePremium delegates to the billing repository`() = runTest {
        val (viewModel, _, billingRepository) = viewModel()
        val activity = mock<Activity>()

        viewModel.purchasePremium(activity)

        verify(billingRepository).purchasePremium(activity)
    }

    @Test
    fun `reloads alerts when a purchase update unlocks premium`() = runTest {
        val purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
        val (_, alertRepository, billingRepository) = viewModel(purchaseUpdates = purchaseUpdates)
        val purchase = mock<Purchase>()
        whenever(billingRepository.verify(purchase)).thenReturn(true)

        purchaseUpdates.tryEmit(listOf(purchase))

        verify(alertRepository, org.mockito.kotlin.atLeast(2)).fetchAll()
    }

    @Test
    fun `does not reload alerts when a purchase update fails verification`() = runTest {
        val purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
        val (_, alertRepository, billingRepository) = viewModel(purchaseUpdates = purchaseUpdates)
        val purchase = mock<Purchase>()
        whenever(billingRepository.verify(purchase)).thenReturn(false)

        purchaseUpdates.tryEmit(listOf(purchase))

        verify(alertRepository, org.mockito.kotlin.times(1)).fetchAll()
    }
}
