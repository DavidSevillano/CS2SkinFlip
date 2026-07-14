package com.burixer85.cs2skinflip.features.skindetail

import androidx.lifecycle.SavedStateHandle
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SkinDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSkin sets Error state instead of crashing when the repository throws`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenThrow(RuntimeException("no network"))
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SkinDetailUiState.Error)
    }

    @Test
    fun `loadSkin sets a not-found message when the backend returns 404`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        val notFound = HttpException(Response.error<Any>(404, "".toResponseBody(null)))
        whenever(skinRepository.getSkinById("missing-skin")).thenThrow(notFound)
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "missing-skin"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SkinDetailUiState.Error(R.string.skindetail_not_found), viewModel.uiState.value)
    }

    @Test
    fun `loadSkin shows a no-connection message for IOExceptions`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenAnswer { throw IOException("Unable to resolve host") }
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            SkinDetailUiState.Error(R.string.error_no_internet),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `loadSkin shows a generic message for other failures`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenThrow(RuntimeException("Internal Server Error"))
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            SkinDetailUiState.Error(R.string.error_generic_retry),
            viewModel.uiState.value,
        )
    }

    private fun fakeSkin(marketHashName: String) = Skin(
        id = "ak-47-redline",
        name = "AK-47 | Redline",
        marketHashName = marketHashName,
        weapon = "AK-47",
        skinName = "Redline",
        rarity = SkinRarity.CLASSIFIED,
        wear = SkinWear.FIELD_TESTED,
        imageUrl = "",
        skinportPrice = 10.0,
        csgoMarketPrice = null,
        waxpeerPrice = null,
        lowestPrice = 10.0,
        priceChange24h = null,
        volume24h = 0,
        floatMin = 0f,
        floatMax = 1f,
        floatMedian = 0.5f,
    )

    @Test
    fun `loadSteamPrice fires after the skin loads, using its marketHashName, and sets Available`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenReturn(fakeSkin("AK-47 | Redline (Field-Tested)"))
        whenever(skinRepository.getSteamPrice("AK-47 | Redline (Field-Tested)")).thenReturn(32.39)
        val watchlistRepository = mock<WatchlistRepository>()
        whenever(watchlistRepository.isInWatchlist("ak-47-redline")).thenReturn(false)
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SteamPriceState.Available(32.39), viewModel.steamPriceState.value)
    }

    @Test
    fun `loadSteamPrice sets Unavailable when the repository returns null`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenReturn(fakeSkin("AK-47 | Redline (Field-Tested)"))
        whenever(skinRepository.getSteamPrice("AK-47 | Redline (Field-Tested)")).thenReturn(null)
        val watchlistRepository = mock<WatchlistRepository>()
        whenever(watchlistRepository.isInWatchlist("ak-47-redline")).thenReturn(false)
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SteamPriceState.Unavailable, viewModel.steamPriceState.value)
    }

    @Test
    fun `Steam price is never fetched when the main skin load fails`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenThrow(RuntimeException("no network"))
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SkinDetailUiState.Error)
        assertEquals(SteamPriceState.Loading, viewModel.steamPriceState.value)
        verify(skinRepository, never()).getSteamPrice(any())
    }

    @Test
    fun `loadSteamPrice failure does not corrupt the already-successful uiState`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenReturn(fakeSkin("AK-47 | Redline (Field-Tested)"))
        whenever(skinRepository.getSteamPrice("AK-47 | Redline (Field-Tested)")).thenThrow(RuntimeException("steam timeout"))
        val watchlistRepository = mock<WatchlistRepository>()
        whenever(watchlistRepository.isInWatchlist("ak-47-redline")).thenReturn(false)
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SkinDetailUiState.Success)
        assertEquals(SteamPriceState.Unavailable, viewModel.steamPriceState.value)
    }
}
