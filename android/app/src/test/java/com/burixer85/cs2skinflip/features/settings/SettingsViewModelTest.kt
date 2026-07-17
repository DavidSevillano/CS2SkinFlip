package com.burixer85.cs2skinflip.features.settings

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import com.burixer85.cs2skinflip.core.steam.SteamSessionManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun meResponse(isPremium: Boolean = false) = MeResponseDto(
        id = "user-1",
        steamId = "76561198000000000",
        username = "tester",
        avatarUrl = null,
        isPremium = isPremium,
        premiumUntil = null,
    )

    private data class Deps(
        val viewModel: SettingsViewModel,
        val backendApi: CS2BackendApiService,
        val billingRepository: BillingRepository,
        val steamSession: SteamSessionManager,
    )

    /** A SteamSessionManager backed by a mutable in-memory cookie jar. */
    private fun fakeSession(initialCookies: String?) = SteamSessionManager(mock()).apply {
        var jar = initialCookies
        readCookies = { jar }
        writeCookie = { _, cookie -> if (cookie.contains("1970")) jar = null }
        userAgentProvider = { "ua" }
    }

    private suspend fun viewModel(
        loggedIn: Boolean = true,
        purchaseUpdates: MutableSharedFlow<List<Purchase>> = MutableSharedFlow(extraBufferCapacity = 1),
        syncPendingPurchases: Boolean = false,
        steamSession: SteamSessionManager = fakeSession(null),
    ): Deps {
        val preferences = mock<UserPreferences>()
        val authRepository = mock<AuthRepository>()
        val backendApi = mock<CS2BackendApiService>()
        val billingRepository = mock<BillingRepository>()

        whenever(preferences.marketplace).thenReturn(flowOf(DefaultMarketplace.LOWEST))
        whenever(authRepository.isLoggedIn).thenReturn(flowOf(loggedIn))
        whenever(billingRepository.purchaseUpdates).thenReturn(purchaseUpdates)
        whenever(billingRepository.syncPendingPurchases()).thenReturn(syncPendingPurchases)
        whenever(backendApi.getMe()).thenReturn(meResponse())

        val viewModel = SettingsViewModel(preferences, authRepository, backendApi, billingRepository, steamSession)
        return Deps(viewModel, backendApi, billingRepository, steamSession)
    }

    @Test
    fun `signing out also disconnects the Steam session`() = runTest {
        val session = fakeSession("steamLoginSecure=tok")
        val deps = viewModel(steamSession = session)
        deps.viewModel.refreshSteamSession()
        assertTrue(deps.viewModel.steamSessionConnected.value)

        deps.viewModel.signOut()

        assertFalse(session.isConnected())
        assertFalse(deps.viewModel.steamSessionConnected.value)
    }

    @Test
    fun `loads the user when logged in`() = runTest {
        val deps = viewModel(loggedIn = true)

        val state = deps.viewModel.accountState.value
        assertEquals("user-1", (state as? AccountUiState.SignedIn)?.user?.id)
    }

    @Test
    fun `clears the user when not logged in`() = runTest {
        val deps = viewModel(loggedIn = false)

        assertEquals(AccountUiState.SignedOut, deps.viewModel.accountState.value)
    }

    @Test
    fun `refetches the user when a pending purchase unlocks premium on login`() = runTest {
        val deps = viewModel(loggedIn = true, syncPendingPurchases = true)

        verify(deps.backendApi, times(2)).getMe()
    }

    @Test
    fun `does not refetch the user when there is no pending purchase`() = runTest {
        val deps = viewModel(loggedIn = true, syncPendingPurchases = false)

        verify(deps.backendApi, times(1)).getMe()
    }

    @Test
    fun `purchasePremium delegates to the billing repository`() = runTest {
        val deps = viewModel()
        val activity = mock<Activity>()
        whenever(deps.billingRepository.purchasePremium(activity)).thenReturn(true)

        deps.viewModel.purchasePremium(activity)

        verify(deps.billingRepository).purchasePremium(activity)
        assertNull(deps.viewModel.purchaseError.value)
    }

    @Test
    fun `purchasePremium surfaces an error when billing couldn't be launched`() = runTest {
        val deps = viewModel()
        val activity = mock<Activity>()
        whenever(deps.billingRepository.purchasePremium(activity)).thenReturn(false)

        deps.viewModel.purchasePremium(activity)

        assertEquals(
            "Couldn't start the purchase. Check your connection and try again.",
            deps.viewModel.purchaseError.value,
        )
    }

    @Test
    fun `refetches the user when a purchase update unlocks premium`() = runTest {
        val purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
        val deps = viewModel(purchaseUpdates = purchaseUpdates)
        val purchase = mock<Purchase>()
        whenever(deps.billingRepository.verify(purchase)).thenReturn(true)

        purchaseUpdates.tryEmit(listOf(purchase))

        verify(deps.backendApi, org.mockito.kotlin.atLeast(2)).getMe()
    }

    @Test
    fun `does not refetch the user when a purchase update fails verification`() = runTest {
        val purchaseUpdates = MutableSharedFlow<List<Purchase>>(extraBufferCapacity = 1)
        val deps = viewModel(purchaseUpdates = purchaseUpdates)
        val purchase = mock<Purchase>()
        whenever(deps.billingRepository.verify(purchase)).thenReturn(false)

        purchaseUpdates.tryEmit(listOf(purchase))

        verify(deps.backendApi, times(1)).getMe()
    }
}
