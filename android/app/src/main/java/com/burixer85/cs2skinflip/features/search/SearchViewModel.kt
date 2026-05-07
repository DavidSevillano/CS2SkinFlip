package com.burixer85.cs2skinflip.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.domain.model.SkinWear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchFilters(
    val weapon: String? = null,
    val rarity: SkinRarity? = null,
    val wear: SkinWear? = null,
    val statTrakOnly: Boolean = false
)

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<Skin>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val skinRepository: SkinRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    val availableWeapons = skinRepository.getAllWeapons()

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    val f = _filters.value
                    _uiState.value = SearchUiState.Loading
                    skinRepository.searchSkins(q, f.weapon, f.rarity, f.wear, f.statTrakOnly)
                }
                .catch { e -> _uiState.value = SearchUiState.Error(e.message ?: "Error") }
                .collect { results ->
                    _uiState.value = SearchUiState.Success(results)
                }
        }
    }

    fun onQueryChange(query: String) { _query.value = query }

    fun onFiltersChange(filters: SearchFilters) {
        _filters.value = filters
        triggerSearch()
    }

    private fun triggerSearch() {
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            val q = _query.value
            val f = _filters.value
            skinRepository.searchSkins(q, f.weapon, f.rarity, f.wear, f.statTrakOnly)
                .catch { e -> _uiState.value = SearchUiState.Error(e.message ?: "Error") }
                .collect { results -> _uiState.value = SearchUiState.Success(results) }
        }
    }
}
