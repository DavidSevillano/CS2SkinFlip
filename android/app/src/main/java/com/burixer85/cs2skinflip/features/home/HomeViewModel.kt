package com.burixer85.cs2skinflip.features.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.util.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TopMoversDirection(val apiValue: String) {
    RISING("rising"),
    FALLING("falling")
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val trendingSkins: List<Skin>) : HomeUiState()
    data class Error(@StringRes val messageRes: Int) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val skinRepository: SkinRepository
) : ViewModel() {

    private val _direction = MutableStateFlow(TopMoversDirection.RISING)
    val direction: StateFlow<TopMoversDirection> = _direction

    // Each direction keeps its own state so swiping the pager between them shows
    // already-loaded content instantly instead of both pages sharing one state.
    private val _risingState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val risingState: StateFlow<HomeUiState> = _risingState

    private val _fallingState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val fallingState: StateFlow<HomeUiState> = _fallingState

    private var risingJob: Job? = null
    private var fallingJob: Job? = null
    private var hasLoadedFalling = false

    init {
        loadRising()
    }

    /** Called when the user taps a tab or swipes the pager to a different page. */
    fun selectDirection(newDirection: TopMoversDirection) {
        _direction.value = newDirection
        if (newDirection == TopMoversDirection.FALLING && !hasLoadedFalling) {
            loadFalling()
        }
    }

    /** Pull-to-refresh: reloads whichever direction is currently visible. */
    fun loadTrending() {
        when (_direction.value) {
            TopMoversDirection.RISING -> loadRising()
            TopMoversDirection.FALLING -> loadFalling()
        }
    }

    private fun loadRising() {
        risingJob?.cancel()
        risingJob = viewModelScope.launch {
            _risingState.value = HomeUiState.Loading
            skinRepository.getTrendingSkins(TopMoversDirection.RISING.apiValue)
                .catch { e -> _risingState.value = HomeUiState.Error(e.toUserMessageRes()) }
                .collect { skins -> _risingState.value = HomeUiState.Success(skins) }
        }
    }

    private fun loadFalling() {
        hasLoadedFalling = true
        fallingJob?.cancel()
        fallingJob = viewModelScope.launch {
            _fallingState.value = HomeUiState.Loading
            skinRepository.getTrendingSkins(TopMoversDirection.FALLING.apiValue)
                .catch { e -> _fallingState.value = HomeUiState.Error(e.toUserMessageRes()) }
                .collect { skins -> _fallingState.value = HomeUiState.Success(skins) }
        }
    }
}
