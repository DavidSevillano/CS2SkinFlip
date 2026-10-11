package com.burixer85.cs2skinflip.features.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.util.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20
private const val SUGGESTION_COUNT = 6

enum class SortBy(@StringRes val labelRes: Int, val apiValue: String) {
    PRICE_DESC(R.string.sort_price_desc, "price_desc"),
    PRICE_ASC(R.string.sort_price_asc, "price_asc"),
    NAME(R.string.sort_name, "name"),
}

data class SearchFilters(
    val wear: SkinWear? = null,
    val rarity: SkinRarity? = null,
    val weapon: String? = null,
    val statTrakOnly: Boolean = false,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sortBy: SortBy = SortBy.PRICE_DESC,
) {
    val activeCount: Int get() = listOfNotNull(
        wear,
        rarity,
        weapon,
        if (statTrakOnly) true else null,
        if (minPrice != null || maxPrice != null) true else null,
    ).size
}

sealed class SearchUiState {
    object Loading : SearchUiState()
    data class Success(
        val results: List<Skin>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val totalCount: Int = 0,
    ) : SearchUiState()
    data class Error(@StringRes val messageRes: Int) : SearchUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val skinRepository: SkinRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _availableWeapons = MutableStateFlow<List<String>>(emptyList())
    val availableWeapons: StateFlow<List<String>> = _availableWeapons

    /**
     * Skin suggestions for the autocomplete dropdown: the first results of the
     * main search for the current query. Not a separate request — the search
     * route is rate-limited per IP (shared behind mobile carrier NAT), and a
     * second lookup on every pause in typing spent that budget twice.
     */
    private val _suggestions = MutableStateFlow<List<Skin>>(emptyList())
    val suggestions: StateFlow<List<Skin>> = _suggestions

    /** True while the search behind the suggestions is running (drives the small spinner). */
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading

    private var currentPage = 1
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            _availableWeapons.value = try {
                skinRepository.getAllWeapons()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        triggerSearch()
    }

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isBlank()) clearSuggestions()
        triggerSearch(debounceMs = 300)
    }

    /** Hides the suggestions (e.g. when the dropdown closes). */
    fun clearSuggestions() {
        _suggestions.value = emptyList()
        _suggestionsLoading.value = false
    }

    fun onFiltersChange(f: SearchFilters) {
        _filters.value = f
        triggerSearch()
    }

    fun clearFilters() {
        _filters.value = SearchFilters(sortBy = _filters.value.sortBy)
        triggerSearch()
    }

    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        val nextPage = currentPage + 1
        loadMoreJob = viewModelScope.launch {
            try {
                val (skins, hasMore, total) = skinRepository.searchSkinsPage(
                    query = _query.value,
                    filters = _filters.value,
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                // Advanced only once the page is in: a failed page is retried
                // on the next scroll instead of being skipped.
                currentPage = nextPage
                // Deduplicate by ID to guard against the same item appearing on two pages
                // (e.g., a row inserted between fetches shifts the OFFSET window).
                val existingIds = current.results.map { it.id }.toHashSet()
                val newSkins = skins.filter { it.id !in existingIds }
                _uiState.value = SearchUiState.Success(
                    results = current.results + newSkins,
                    isLoadingMore = false,
                    hasMore = hasMore,
                    totalCount = total,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = current.copy(isLoadingMore = false)
            }
        }
    }

    private fun triggerSearch(debounceMs: Long = 0) {
        searchJob?.cancel()
        // A next page still loading belongs to the previous query; letting it
        // finish would append its results to the new ones.
        loadMoreJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val query = _query.value
            currentPage = 1
            _uiState.value = SearchUiState.Loading
            _suggestionsLoading.value = query.isNotBlank()
            try {
                val (skins, hasMore, total) = skinRepository.searchSkinsPage(
                    query = query,
                    filters = _filters.value,
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _uiState.value = SearchUiState.Success(
                    results = skins,
                    hasMore = hasMore,
                    totalCount = total,
                )
                _suggestions.value = if (query.isBlank()) emptyList() else skins.take(SUGGESTION_COUNT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.toUserMessageRes())
                _suggestions.value = emptyList()
            } finally {
                // A cancelled search leaves the flag to the one that replaced it.
                if (isActive) _suggestionsLoading.value = false
            }
        }
    }
}
