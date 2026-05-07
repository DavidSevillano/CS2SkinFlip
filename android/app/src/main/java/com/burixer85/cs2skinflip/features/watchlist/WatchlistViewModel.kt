package com.burixer85.cs2skinflip.features.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WatchlistUiState {
    object Loading : WatchlistUiState()
    data class Success(val items: List<WatchlistItem>) : WatchlistUiState()
    data class Error(val message: String) : WatchlistUiState()
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WatchlistUiState>(WatchlistUiState.Loading)
    val uiState: StateFlow<WatchlistUiState> = _uiState

    init {
        // Pre-seed with mock data on first run for demo purposes
        viewModelScope.launch {
            if (watchlistRepository.isInWatchlist(MockData.mockWatchlist[0].skinId).not()) {
                MockData.mockWatchlist.forEach { item ->
                    watchlistRepository.add(item)
                }
            }
        }
        observeWatchlist()
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistRepository.getAll()
                .catch { e -> _uiState.value = WatchlistUiState.Error(e.message ?: "Error") }
                .collect { items -> _uiState.value = WatchlistUiState.Success(items) }
        }
    }

    fun removeItem(id: Long) {
        viewModelScope.launch { watchlistRepository.remove(id) }
    }

    fun toggleAlert(id: Long, enabled: Boolean) {
        viewModelScope.launch { watchlistRepository.toggleAlert(id, enabled) }
    }
}
