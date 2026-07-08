package com.burixer85.cs2skinflip.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

enum class SortBy(val label: String, val apiValue: String) {
    PRICE_DESC("Price: High → Low", "price_desc"),
    PRICE_ASC("Price: Low → High", "price_asc"),
    NAME("Name A → Z", "name"),
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
    data class Error(val message: String) : SearchUiState()
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

    /** Live skin suggestions shown in the autocomplete dropdown — debounced, top 6. */
    private val _suggestions = MutableStateFlow<List<Skin>>(emptyList())
    val suggestions: StateFlow<List<Skin>> = _suggestions

    /** True while the autocomplete is fetching (drives the small spinner). */
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading

    private var currentPage = 1
    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            _availableWeapons.value = runCatching { skinRepository.getAllWeapons() }.getOrDefault(emptyList())
        }
        triggerSearch(resetPage = true)
    }

    fun onQueryChange(q: String) {
        _query.value = q
        triggerSearch(resetPage = true, debounceMs = 300)
        triggerSuggestions(q)
    }

    /** Cancels any pending suggestion lookups (e.g. when the dropdown closes). */
    fun clearSuggestions() {
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        _suggestionsLoading.value = false
    }

    private fun triggerSuggestions(q: String) {
        suggestJob?.cancel()
        if (q.isBlank()) {
            _suggestions.value = emptyList()
            _suggestionsLoading.value = false
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)  // debounce — slightly tighter than the main search to feel snappy
            _suggestionsLoading.value = true
            runCatching {
                skinRepository.searchSkinsPage(
                    query = q,
                    filters = SearchFilters(),  // ignore active filters in the suggestion list
                    page = 1,
                    limit = 6,
                )
            }.onSuccess { (skins, _, _) ->
                _suggestions.value = skins
            }.onFailure {
                _suggestions.value = emptyList()
            }
            _suggestionsLoading.value = false
        }
    }

    fun onFiltersChange(f: SearchFilters) {
        _filters.value = f
        triggerSearch(resetPage = true)
    }

    fun clearFilters() {
        _filters.value = SearchFilters(sortBy = _filters.value.sortBy)
        triggerSearch(resetPage = true)
    }

    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        currentPage++
        viewModelScope.launch {
            runCatching {
                skinRepository.searchSkinsPage(
                    query = _query.value,
                    filters = _filters.value,
                    page = currentPage,
                    limit = PAGE_SIZE,
                )
            }.onSuccess { (skins, hasMore, total) ->
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
            }.onFailure {
                _uiState.value = current.copy(isLoadingMore = false)
            }
        }
    }

    private fun triggerSearch(resetPage: Boolean = true, debounceMs: Long = 0) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (resetPage) currentPage = 1
            _uiState.value = SearchUiState.Loading
            runCatching {
                skinRepository.searchSkinsPage(
                    query = _query.value,
                    filters = _filters.value,
                    page = 1,
                    limit = PAGE_SIZE,
                )
            }.onSuccess { (skins, hasMore, total) ->
                _uiState.value = SearchUiState.Success(
                    results = skins,
                    hasMore = hasMore,
                    totalCount = total,
                )
            }.onFailure { e ->
                _uiState.value = SearchUiState.Error(e.toUserMessage())
            }
        }
    }
}
