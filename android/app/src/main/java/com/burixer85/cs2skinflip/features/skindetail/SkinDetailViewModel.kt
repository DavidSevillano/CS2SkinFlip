package com.burixer85.cs2skinflip.features.skindetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SkinDetailUiState {
    object Loading : SkinDetailUiState()
    data class Success(val skin: Skin, val isInWatchlist: Boolean) : SkinDetailUiState()
    data class Error(val message: String) : SkinDetailUiState()
}

@HiltViewModel
class SkinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val skinRepository: SkinRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val skinId: String = checkNotNull(savedStateHandle["skinId"])

    private val _uiState = MutableStateFlow<SkinDetailUiState>(SkinDetailUiState.Loading)
    val uiState: StateFlow<SkinDetailUiState> = _uiState

    init {
        loadSkin()
    }

    private fun loadSkin() {
        viewModelScope.launch {
            val skin = skinRepository.getSkinById(skinId)
            if (skin == null) {
                _uiState.value = SkinDetailUiState.Error("Skin not found")
            } else {
                val inWatchlist = watchlistRepository.isInWatchlist(skinId)
                _uiState.value = SkinDetailUiState.Success(skin, inWatchlist)
            }
        }
    }

    fun toggleWatchlist() {
        val state = _uiState.value as? SkinDetailUiState.Success ?: return
        viewModelScope.launch {
            if (state.isInWatchlist) {
                watchlistRepository.removeBySkinId(skinId)
            } else {
                watchlistRepository.add(
                    WatchlistItem(
                        skinId = state.skin.id,
                        skinName = state.skin.name,
                        skinImageUrl = state.skin.imageUrl,
                        targetBuyPrice = null,
                        targetSellPrice = null,
                        currentPrice = state.skin.steamPrice,
                        priceChange24h = state.skin.priceChange24h
                    )
                )
            }
            _uiState.value = state.copy(isInWatchlist = !state.isInWatchlist)
        }
    }
}
