package com.burixer85.cs2skinflip.features.skindetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

sealed class SkinDetailUiState {
    object Loading : SkinDetailUiState()
    data class Success(val skin: Skin, val isInWatchlist: Boolean) : SkinDetailUiState()
    data class Error(val message: String) : SkinDetailUiState()
}

enum class PriceRange(val apiValue: String, val label: String) {
    DAY("24h", "24H"),
    WEEK("7d", "7D"),
    MONTH("30d", "30D"),
}

@HiltViewModel
class SkinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val skinRepository: SkinRepository,
    private val watchlistRepository: WatchlistRepository,
    private val analytics: AnalyticsService,
) : ViewModel() {

    private val skinId: String = checkNotNull(savedStateHandle["skinId"])

    private val _uiState = MutableStateFlow<SkinDetailUiState>(SkinDetailUiState.Loading)
    val uiState: StateFlow<SkinDetailUiState> = _uiState

    private val _selectedRange = MutableStateFlow(PriceRange.DAY)
    val selectedRange: StateFlow<PriceRange> = _selectedRange

    init {
        loadSkin()
    }

    private fun loadSkin() {
        viewModelScope.launch {
            _uiState.value = SkinDetailUiState.Loading
            runCatching { skinRepository.getSkinById(skinId) }
                .onSuccess { skin ->
                    val inWatchlist = watchlistRepository.isInWatchlist(skinId)
                    _uiState.value = SkinDetailUiState.Success(skin, inWatchlist)
                    analytics.logSkinViewed(skin.id, skin.name)
                }
                .onFailure { e ->
                    val message = if (e is HttpException && e.code() == 404) {
                        "Skin not found"
                    } else {
                        e.message ?: "Failed to load skin"
                    }
                    _uiState.value = SkinDetailUiState.Error(message)
                }
        }
    }

    fun retry() = loadSkin()

    fun onRangeSelected(range: PriceRange) {
        if (range == _selectedRange.value) return
        _selectedRange.value = range
        viewModelScope.launch {
            val history = skinRepository.getPriceHistory(skinId, range.apiValue)
            val state = _uiState.value as? SkinDetailUiState.Success ?: return@launch
            _uiState.value = state.copy(skin = state.skin.copy(priceHistory = history))
        }
    }

    fun toggleWatchlist() {
        val state = _uiState.value as? SkinDetailUiState.Success ?: return
        viewModelScope.launch {
            if (state.isInWatchlist) {
                watchlistRepository.removeBySkinId(skinId)
            } else {
                analytics.logSkinAddedToWatchlist(state.skin.id, state.skin.name)
                watchlistRepository.add(
                    WatchlistItem(
                        skinId = state.skin.id,
                        skinName = state.skin.name,
                        skinImageUrl = state.skin.imageUrl,
                        targetBuyPrice = null,
                        targetSellPrice = null,
                        currentPrice = state.skin.lowestMarketPrice,
                        priceChange24h = state.skin.priceChange24h ?: 0.0
                    )
                )
            }
            _uiState.value = state.copy(isInWatchlist = !state.isInWatchlist)
        }
    }
}
