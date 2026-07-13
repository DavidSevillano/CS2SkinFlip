package com.burixer85.cs2skinflip.features.skindetail

import androidx.lifecycle.SavedStateHandle
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
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
import org.mockito.kotlin.mock
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

    @Test
    fun `loadSteamPrice sets Available when the repository returns a price`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenThrow(RuntimeException("stop main load"))
        whenever(skinRepository.getSteamPrice("ak-47-redline")).thenReturn(32.39)
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SteamPriceState.Available(32.39), viewModel.steamPriceState.value)
    }

    @Test
    fun `loadSteamPrice sets Unavailable when the repository returns null`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenThrow(RuntimeException("stop main load"))
        whenever(skinRepository.getSteamPrice("ak-47-redline")).thenReturn(null)
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SteamPriceState.Unavailable, viewModel.steamPriceState.value)
    }

    @Test
    fun `loadSteamPrice failure does not affect the main uiState`() = runTest(dispatcher) {
        val skinRepository = mock<SkinRepository>()
        whenever(skinRepository.getSkinById("ak-47-redline")).thenThrow(RuntimeException("no network"))
        whenever(skinRepository.getSteamPrice("ak-47-redline")).thenThrow(RuntimeException("steam timeout"))
        val watchlistRepository = mock<WatchlistRepository>()
        val analytics = mock<AnalyticsService>()
        val savedStateHandle = SavedStateHandle(mapOf("skinId" to "ak-47-redline"))

        val viewModel = SkinDetailViewModel(savedStateHandle, skinRepository, watchlistRepository, analytics)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SkinDetailUiState.Error)
    }
}
