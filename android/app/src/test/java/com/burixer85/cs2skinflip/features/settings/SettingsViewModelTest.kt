package com.burixer85.cs2skinflip.features.settings

import android.app.Activity
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import com.burixer85.cs2skinflip.core.data.repository.PremiumUnlockSource
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
        premiumUnlocked: MutableSharedFlow<PremiumUnlockSource> = MutableSharedFlow(extraBufferCapacity = 1),
        steamSession: SteamSessionManager = fakeSession(null),
    ): Deps {
        val preferences = mock<UserPreferences>()
        val authRepository = mock<AuthRepository>()
        val backendApi = mock<CS2BackendApiService>()
        val billingRepository = mock<BillingRepository>()

        whenever(preferences.marketplace).thenReturn(flowOf(DefaultMarketplace.LOWEST))
        whenever(authRepository.isLoggedIn).thenReturn(flowOf(loggedIn))
        whenever(billingRepository.premiumUnlocked).thenReturn(premiumUnlocked)
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
    fun `checks for unconfirmed purchases once signed in`() = runTest {
        val deps = viewModel(loggedIn = true)

        verify(deps.billingRepository).syncPendingPurchases()
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
    fun `refetches the user when premium is unlocked`() = runTest {
        val unlocked = MutableSharedFlow<PremiumUnlockSource>(extraBufferCapacity = 1)
        val deps = viewModel(premiumUnlocked = unlocked)

        unlocked.tryEmit(PremiumUnlockSource.RESTORED)

        verify(deps.backendApi, times(2)).getMe()
    }

    @Test
    fun `shows the purchase success dialog for a fresh purchase`() = runTest {
        val unlocked = MutableSharedFlow<PremiumUnlockSource>(extraBufferCapacity = 1)
        val deps = viewModel(premiumUnlocked = unlocked)

        assertFalse(deps.viewModel.showPurchaseSuccessDialog.value)
        unlocked.tryEmit(PremiumUnlockSource.PURCHASE)

        assertTrue(deps.viewModel.showPurchaseSuccessDialog.value)
    }

    // A purchase recovered on sign-in was paid for earlier; celebrating it now
    // would pop a dialog out of nowhere.
    @Test
    fun `does not celebrate a purchase restored on sign-in`() = runTest {
        val unlocked = MutableSharedFlow<PremiumUnlockSource>(extraBufferCapacity = 1)
        val deps = viewModel(premiumUnlocked = unlocked)

        unlocked.tryEmit(PremiumUnlockSource.RESTORED)

        assertFalse(deps.viewModel.showPurchaseSuccessDialog.value)
    }

    @Test
    fun `dismissPurchaseSuccessDialog hides the dialog`() = runTest {
        val unlocked = MutableSharedFlow<PremiumUnlockSource>(extraBufferCapacity = 1)
        val deps = viewModel(premiumUnlocked = unlocked)
        unlocked.tryEmit(PremiumUnlockSource.PURCHASE)
        assertTrue(deps.viewModel.showPurchaseSuccessDialog.value)

        deps.viewModel.dismissPurchaseSuccessDialog()

        assertFalse(deps.viewModel.showPurchaseSuccessDialog.value)
    }
}
