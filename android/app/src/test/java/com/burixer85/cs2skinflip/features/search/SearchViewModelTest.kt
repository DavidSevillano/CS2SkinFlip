package com.burixer85.cs2skinflip.features.search

import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo: SkinRepository = mock()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun skin(id: String) = Skin(
        id = id, name = id, weapon = "AK-47", skinName = id, rarity = SkinRarity.COVERT,
        wear = SkinWear.FIELD_TESTED, imageUrl = "", skinportPrice = 1.0, csgoMarketPrice = null,
        waxpeerPrice = null, priceChange24h = null, volume24h = 0, floatMin = 0f, floatMax = 1f, floatMedian = 0.5f,
    )

    private fun page(vararg ids: String, hasMore: Boolean = true) = Triple(ids.map(::skin), hasMore, 100)

    private suspend fun answers(query: String, pageNo: Int, result: Triple<List<Skin>, Boolean, Int>) {
        whenever(repo.searchSkinsPage(eq(query), any(), eq(pageNo), any())).thenReturn(result)
    }

    private fun results(vm: SearchViewModel) = (vm.uiState.value as SearchUiState.Success).results.map { it.id }

    private suspend fun newViewModel(): SearchViewModel {
        whenever(repo.getAllWeapons()).thenReturn(emptyList())
        answers("", 1, page("a1", "a2"))
        return SearchViewModel(repo)
    }

    // A page requested for the old query must not land on top of the new one.
    @Test
    fun `a next page that arrives after the query changed is discarded`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        val stalePage = CompletableDeferred<Triple<List<Skin>, Boolean, Int>>()
        whenever(repo.searchSkinsPage(eq(""), any(), eq(2), any())).doSuspendableAnswer { stalePage.await() }
        answers("ak", 1, page("ak1"))

        vm.loadMore()
        vm.onQueryChange("ak")
        advanceUntilIdle()
        stalePage.complete(page("a3"))
        advanceUntilIdle()

        assertEquals(listOf("ak1"), results(vm))
    }

    @Test
    fun `a failed next page is retried, not skipped`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        whenever(repo.searchSkinsPage(eq(""), any(), eq(2), any()))
            .thenAnswer { throw IOException("offline") }
            .thenReturn(page("a3"))

        vm.loadMore()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        verify(repo, times(2)).searchSkinsPage(eq(""), any(), eq(2), any())
        assertEquals(listOf("a1", "a2", "a3"), results(vm))
    }

    // The search endpoint is rate-limited per IP (30/min, shared behind mobile
    // carrier NAT); suggestions used to cost a second request on every pause.
    @Test
    fun `typing costs one request per pause, and suggestions come from it`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        answers("ak", 1, page("k1", "k2", "k3", "k4", "k5", "k6", "k7"))

        vm.onQueryChange("ak")
        advanceUntilIdle()

        verify(repo, times(1)).searchSkinsPage(eq("ak"), any(), any(), any())
        assertEquals(listOf("k1", "k2", "k3", "k4", "k5", "k6"), vm.suggestions.value.map { it.id })
    }

    @Test
    fun `no suggestions without a query`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(emptyList<Skin>(), vm.suggestions.value)
        verify(repo, times(0)).searchSkinsPage(anyOrNull(), any(), eq(1), eq(6))
    }
}
