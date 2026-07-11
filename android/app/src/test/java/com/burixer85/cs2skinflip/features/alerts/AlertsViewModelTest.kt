package com.burixer85.cs2skinflip.features.alerts

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.repository.AlertRepository
import com.burixer85.cs2skinflip.core.data.repository.AlertsState
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var defaultLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The ViewModel prefills prices with "%.2f".format(), which is locale-sensitive.
        // Pin to US so decimal formatting is deterministic across dev machines / CI.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
    }

    // ─── Test harness ─────────────────────────────────────────────────────────

    private data class Harness(
        val viewModel: AlertsViewModel,
        val alertRepository: AlertRepository,
        val authRepository: AuthRepository,
        val skinRepository: SkinRepository,
        val analytics: AnalyticsService,
        val billingRepository: BillingRepository,
    )

    private suspend fun build(
        loggedIn: Boolean = true,
        purchaseUpdates: MutableSharedFlow<List<Purchase>> = MutableSharedFlow(extraBufferCapacity = 1),
        fetchResult: AlertsState = AlertsState(alerts = emptyList(), isPremium = false, activeCount = 0, freeLimit = 1),
        syncPendingPurchases: Boolean = false,
    ): Harness {
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
        return Harness(viewModel, alertRepository, authRepository, skinRepository, analytics, billingRepository)
    }

    private fun skin(
        id: String = "ak-47-redline",
        name: String = "AK-47 | Redline",
        lowest: Double? = null,
    ) = Skin(
        id = id,
        name = name,
        weapon = "AK-47",
        skinName = "Redline",
        rarity = SkinRarity.CLASSIFIED,
        wear = SkinWear.FIELD_TESTED,
        imageUrl = "",
        skinportPrice = lowest,
        csgoMarketPrice = null,
        waxpeerPrice = null,
        lowestPrice = lowest,
        priceChange24h = null,
        volume24h = 0,
        floatMin = 0f,
        floatMax = 1f,
        floatMedian = 0.5f,
    )

    private fun alert(
        id: String = "alert-1",
        type: AlertType = AlertType.BUY_BELOW,
        targetPrice: Double = 12.34,
        isActive: Boolean = true,
    ) = Alert(
        id = id,
        skinId = "ak-47-redline",
        skinName = "AK-47 | Redline",
        skinImageUrl = "",
        type = type,
        targetPrice = targetPrice,
        currentPrice = 0.0,
        isActive = isActive,
    )

    private fun unauthorized(): HttpException {
        val body = "".toResponseBody("application/json".toMediaTypeOrNull())
        return HttpException(Response.error<Any>(401, body))
    }

    // ─── Init / purchase syncing ──────────────────────────────────────────────

    @Test
    fun `loads alerts on init when the user is logged in`() = runTest {
        val h = build(loggedIn = true)

        assertTrue(h.viewModel.uiState.value is AlertsUiState.Success)
    }

    @Test
    fun `shows NotLoggedIn state when the user is not logged in`() = runTest {
        val h = build(loggedIn = false)

        assertEquals(AlertsUiState.NotLoggedIn, h.viewModel.uiState.value)
    }

    @Test
    fun `reloads alerts when a pending purchase is synced on login`() = runTest {
        val h = build(loggedIn = true, syncPendingPurchases = true)

        verify(h.alertRepository, times(2)).fetchAll()
    }

    @Test
    fun `does not reload twice when there is no pending purchase to sync`() = runTest {
        val h = build(loggedIn = true, syncPendingPurchases = false)

        verify(h.alertRepository, times(1)).fetchAll()
    }

    @Test
    fun `purchasePremium delegates to the billing repository`() = runTest {
        val h = build()
        val activity = mock<Activity>()

        h.viewModel.purchasePremium(activity)

        verify(h.billingRepository).purchasePremium(activity)
    }

    @Test
    fun `reloads alerts when a purchase update unlocks premium`() = runTest {
        val purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
        val h = build(purchaseUpdates = purchaseUpdates)
        val purchase = mock<Purchase>()
        whenever(h.billingRepository.verify(purchase)).thenReturn(true)

        purchaseUpdates.tryEmit(listOf(purchase))

        verify(h.alertRepository, atLeast(2)).fetchAll()
    }

    @Test
    fun `does not reload alerts when a purchase update fails verification`() = runTest {
        val purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
        val h = build(purchaseUpdates = purchaseUpdates)
        val purchase = mock<Purchase>()
        whenever(h.billingRepository.verify(purchase)).thenReturn(false)

        purchaseUpdates.tryEmit(listOf(purchase))

        verify(h.alertRepository, times(1)).fetchAll()
    }

    // ─── loadAlerts error mapping ─────────────────────────────────────────────

    @Test
    fun `loadAlerts maps a 401 to NotLoggedIn`() = runTest {
        val h = build()
        whenever(h.alertRepository.fetchAll()).thenThrow(unauthorized())

        h.viewModel.loadAlerts()

        assertEquals(AlertsUiState.NotLoggedIn, h.viewModel.uiState.value)
    }

    @Test
    fun `loadAlerts maps a non-auth failure to Error`() = runTest {
        val h = build()
        whenever(h.alertRepository.fetchAll()).thenThrow(RuntimeException("boom"))

        h.viewModel.loadAlerts()

        assertTrue(h.viewModel.uiState.value is AlertsUiState.Error)
    }

    // ─── atFreeLimit computed property ────────────────────────────────────────

    @Test
    fun `atFreeLimit is true when a free user has reached the cap`() {
        val state = AlertsUiState.Success(alerts = listOf(alert()), isPremium = false, freeLimit = 1)

        assertTrue(state.atFreeLimit)
    }

    @Test
    fun `atFreeLimit is false when a free user is below the cap`() {
        val state = AlertsUiState.Success(alerts = emptyList(), isPremium = false, freeLimit = 1)

        assertFalse(state.atFreeLimit)
    }

    @Test
    fun `atFreeLimit is false for a premium user with unlimited alerts`() {
        val state = AlertsUiState.Success(
            alerts = listOf(alert("a"), alert("b"), alert("c")),
            isPremium = true,
            freeLimit = null,
        )

        assertFalse(state.atFreeLimit)
    }

    // ─── Create alert flow ────────────────────────────────────────────────────

    @Test
    fun `submitCreateAlert rejects when no skin is selected`() = runTest {
        val h = build()

        val ok = h.viewModel.submitCreateAlert()

        assertFalse(ok)
        assertNull(h.viewModel.createState.value.selectedSkin)
        assertTrue(h.viewModel.createState.value.errorMessageRes != null)
        verify(h.alertRepository, never()).create(any(), any(), any())
    }

    @Test
    fun `submitCreateAlert rejects a non-positive target price`() = runTest {
        val h = build()
        h.viewModel.onCreateSkinSelected(skin())
        h.viewModel.onCreateTargetPriceChange("0")

        val ok = h.viewModel.submitCreateAlert()

        assertFalse(ok)
        assertTrue(h.viewModel.createState.value.errorMessageRes != null)
        verify(h.alertRepository, never()).create(any(), any(), any())
    }

    @Test
    fun `submitCreateAlert creates the alert, logs analytics and resets on success`() = runTest {
        val h = build()
        whenever(h.alertRepository.create(any(), any(), any())).thenReturn(alert())
        val s = skin(id = "ak-47-redline", name = "AK-47 | Redline")
        h.viewModel.onCreateSkinSelected(s)
        h.viewModel.onCreateTargetPriceChange("15.50")

        val ok = h.viewModel.submitCreateAlert()

        assertTrue(ok)
        verify(h.alertRepository).create("ak-47-redline", AlertType.BUY_BELOW, 15.50)
        verify(h.analytics).logAlertCreated("ak-47-redline", "AK-47 | Redline", "BUY_BELOW", 15.50)
        // create state reset
        assertNull(h.viewModel.createState.value.selectedSkin)
        assertEquals("", h.viewModel.createState.value.targetPrice)
        // list reloaded (init + after create)
        verify(h.alertRepository, times(2)).fetchAll()
    }

    @Test
    fun `submitCreateAlert on 401 switches to NotLoggedIn and resets the sheet`() = runTest {
        val h = build()
        whenever(h.alertRepository.create(any(), any(), any())).thenThrow(unauthorized())
        h.viewModel.onCreateSkinSelected(skin())
        h.viewModel.onCreateTargetPriceChange("15")

        val ok = h.viewModel.submitCreateAlert()

        assertFalse(ok)
        assertEquals(AlertsUiState.NotLoggedIn, h.viewModel.uiState.value)
        assertNull(h.viewModel.createState.value.selectedSkin)
    }

    @Test
    fun `submitCreateAlert surfaces a non-auth failure as an error on the sheet`() = runTest {
        val h = build()
        whenever(h.alertRepository.create(any(), any(), any())).thenThrow(RuntimeException("nope"))
        h.viewModel.onCreateSkinSelected(skin())
        h.viewModel.onCreateTargetPriceChange("15")

        val ok = h.viewModel.submitCreateAlert()

        assertFalse(ok)
        assertFalse(h.viewModel.createState.value.submitting)
        assertTrue(h.viewModel.createState.value.errorMessageRes != null)
        // still selected so the user can retry
        assertTrue(h.viewModel.createState.value.selectedSkin != null)
    }

    @Test
    fun `onCreateSkinSelected prefills the query and target price from the lowest market price`() = runTest {
        val h = build()

        h.viewModel.onCreateSkinSelected(skin(name = "AWP | Asiimov", lowest = 42.0))

        val state = h.viewModel.createState.value
        assertEquals("AWP | Asiimov", state.query)
        assertEquals("42.00", state.targetPrice)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `onCreateSkinSelected prefills a dot-decimal price even on a comma-decimal locale`() = runTest {
        // Regression: on comma-decimal locales the prefill must still use '.' so it round-trips
        // through toDoubleOrNull() at submit instead of failing validation.
        Locale.setDefault(Locale.GERMANY)
        val h = build()
        whenever(h.alertRepository.create(any(), any(), any())).thenReturn(alert())

        h.viewModel.onCreateSkinSelected(skin(lowest = 42.0))
        assertEquals("42.00", h.viewModel.createState.value.targetPrice)

        // and the prefilled value submits successfully rather than tripping the price validation
        val ok = h.viewModel.submitCreateAlert()
        assertTrue(ok)
        verify(h.alertRepository).create(any(), any(), eq(42.0))
    }

    @Test
    fun `onCreateSkinSelected leaves the target price blank when there is no price`() = runTest {
        val h = build()

        h.viewModel.onCreateSkinSelected(skin(lowest = null))

        assertEquals("", h.viewModel.createState.value.targetPrice)
    }

    @Test
    fun `onCreateTargetPriceChange strips non-numeric characters`() = runTest {
        val h = build()

        h.viewModel.onCreateTargetPriceChange("1a2.b3\$")

        assertEquals("12.3", h.viewModel.createState.value.targetPrice)
    }

    @Test
    fun `onCreateQueryChange with a blank query clears results`() = runTest {
        val h = build()

        h.viewModel.onCreateQueryChange("   ")

        val state = h.viewModel.createState.value
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `onCreateQueryChange searches after the debounce and populates results`() = runTest(dispatcher) {
        val h = build()
        val results = listOf(skin())
        whenever(h.skinRepository.searchSkinsPage(any(), any(), any(), any()))
            .thenReturn(Triple(results, false, 1))

        h.viewModel.onCreateQueryChange("red")
        advanceUntilIdle()

        assertEquals(results, h.viewModel.createState.value.results)
        assertFalse(h.viewModel.createState.value.isSearching)
    }

    // ─── Edit alert flow ──────────────────────────────────────────────────────

    @Test
    fun `startEdit populates the edit sheet from the alert`() = runTest {
        val h = build()

        h.viewModel.startEdit(alert(type = AlertType.SELL_ABOVE, targetPrice = 9.5))

        val state = h.viewModel.editState.value
        assertTrue(state.alert != null)
        assertEquals(AlertType.SELL_ABOVE, state.type)
        assertEquals("9.50", state.targetPrice)
    }

    @Test
    fun `submitEdit does nothing when the sheet is not open`() = runTest {
        val h = build()

        val ok = h.viewModel.submitEdit()

        assertFalse(ok)
        verify(h.alertRepository, never()).update(any(), any(), any())
    }

    @Test
    fun `submitEdit rejects a non-positive target price`() = runTest {
        val h = build()
        h.viewModel.startEdit(alert())
        h.viewModel.onEditTargetPriceChange("0")

        val ok = h.viewModel.submitEdit()

        assertFalse(ok)
        assertTrue(h.viewModel.editState.value.errorMessageRes != null)
        verify(h.alertRepository, never()).update(any(), any(), any())
    }

    @Test
    fun `submitEdit updates the alert, logs analytics and closes the sheet on success`() = runTest {
        val h = build()
        whenever(h.alertRepository.update(any(), any(), any())).thenReturn(alert())
        h.viewModel.startEdit(alert(id = "alert-1", type = AlertType.BUY_BELOW))
        h.viewModel.onEditTypeChange(AlertType.SELL_ABOVE)
        h.viewModel.onEditTargetPriceChange("20.00")

        val ok = h.viewModel.submitEdit()

        assertTrue(ok)
        verify(h.alertRepository).update("alert-1", AlertType.SELL_ABOVE, 20.00)
        verify(h.analytics).logAlertEdited("alert-1", "SELL_ABOVE", 20.00)
        assertNull(h.viewModel.editState.value.alert)
        verify(h.alertRepository, times(2)).fetchAll()
    }

    @Test
    fun `submitEdit on 401 switches to NotLoggedIn and closes the sheet`() = runTest {
        val h = build()
        whenever(h.alertRepository.update(any(), any(), any())).thenThrow(unauthorized())
        h.viewModel.startEdit(alert())
        h.viewModel.onEditTargetPriceChange("20")

        val ok = h.viewModel.submitEdit()

        assertFalse(ok)
        assertEquals(AlertsUiState.NotLoggedIn, h.viewModel.uiState.value)
        assertNull(h.viewModel.editState.value.alert)
    }

    @Test
    fun `onEditTargetPriceChange strips non-numeric characters`() = runTest {
        val h = build()
        h.viewModel.startEdit(alert())

        h.viewModel.onEditTargetPriceChange("9x9.9y")

        assertEquals("99.9", h.viewModel.editState.value.targetPrice)
    }

    // ─── Toggle / delete ──────────────────────────────────────────────────────

    @Test
    fun `toggleAlert flips the active flag and reloads`() = runTest {
        val h = build()
        val a = alert(isActive = true)
        whenever(h.alertRepository.setActive(eq(a.id), eq(false))).thenReturn(a.copy(isActive = false))

        h.viewModel.toggleAlert(a)

        verify(h.alertRepository).setActive(a.id, false)
        verify(h.alertRepository, times(2)).fetchAll()
    }

    @Test
    fun `deleteAlert logs analytics, deletes and reloads`() = runTest {
        val h = build()

        h.viewModel.deleteAlert("alert-9")

        verify(h.analytics).logAlertDeleted("alert-9")
        verify(h.alertRepository).delete("alert-9")
        verify(h.alertRepository, times(2)).fetchAll()
    }
}
