package com.burixer85.cs2skinflip.features.skindetail

import androidx.lifecycle.SavedStateHandle
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
}
