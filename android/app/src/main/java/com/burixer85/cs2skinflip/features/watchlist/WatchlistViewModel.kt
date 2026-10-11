package com.burixer85.cs2skinflip.features.watchlist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import com.burixer85.cs2skinflip.core.util.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WatchlistUiState {
    object Loading : WatchlistUiState()
    data class Success(val items: List<WatchlistItem>) : WatchlistUiState()
    data class Error(@StringRes val messageRes: Int) : WatchlistUiState()
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WatchlistUiState>(WatchlistUiState.Loading)
    val uiState: StateFlow<WatchlistUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        // One-time cleanup: remove pre-seeded mock items from older versions.
        // Their skin IDs don't match real backend cuids, so user-added items aren't affected.
        viewModelScope.launch {
            listOf("m4a4_howl", "awp_dragon_lore", "glock_fade", "butterfly_marble_fade").forEach {
                watchlistRepository.removeBySkinId(it)
            }
        }
        observeWatchlist()
        refreshPrices()
    }

    /** Pulls live prices; on failure the last stored prices simply stay on screen. */
    fun refreshPrices() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                watchlistRepository.refreshPrices()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or backend down — keep showing the cached prices.
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistRepository.getAll()
                .catch { e -> _uiState.value = WatchlistUiState.Error(e.toUserMessageRes()) }
                .collect { items -> _uiState.value = WatchlistUiState.Success(items) }
        }
    }

    fun removeItem(id: Long) {
        viewModelScope.launch { watchlistRepository.remove(id) }
    }
}
