package com.burixer85.cs2skinflip.features.skindetail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.data.repository.WatchlistRepository
import com.burixer85.cs2skinflip.core.steam.SteamSessionManager
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.domain.model.WatchlistItem
import com.burixer85.cs2skinflip.core.util.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

sealed class SkinDetailUiState {
    object Loading : SkinDetailUiState()
    data class Success(val skin: Skin, val isInWatchlist: Boolean) : SkinDetailUiState()
    data class Error(@StringRes val messageRes: Int) : SkinDetailUiState()
}

enum class PriceRange(val apiValue: String, val label: String) {
    WEEK("7d", "7D"),
    MONTH("30d", "30D"),
    QUARTER("90d", "3M"),
}

sealed class SteamPriceState {
    object Loading : SteamPriceState()
    data class Available(val price: Double) : SteamPriceState()
    object Unavailable : SteamPriceState()
}

@HiltViewModel
class SkinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val skinRepository: SkinRepository,
    private val watchlistRepository: WatchlistRepository,
    private val analytics: AnalyticsService,
    private val steamSession: SteamSessionManager,
) : ViewModel() {

    private val skinId: String = checkNotNull(savedStateHandle["skinId"])

    private val _uiState = MutableStateFlow<SkinDetailUiState>(SkinDetailUiState.Loading)
    val uiState: StateFlow<SkinDetailUiState> = _uiState

    private val _selectedRange = MutableStateFlow(PriceRange.WEEK)
    val selectedRange: StateFlow<PriceRange> = _selectedRange

    private val _steamPriceState = MutableStateFlow<SteamPriceState>(SteamPriceState.Loading)
    val steamPriceState: StateFlow<SteamPriceState> = _steamPriceState

    // Drives the "connect your Steam session" hint under the Steam price row.
    private val _steamSessionConnected = MutableStateFlow(false)
    val steamSessionConnected: StateFlow<Boolean> = _steamSessionConnected

    init {
        loadSkin()
    }

    private fun loadSkin() {
        viewModelScope.launch {
            _uiState.value = SkinDetailUiState.Loading
            _steamPriceState.value = SteamPriceState.Loading
            runCatching { skinRepository.getSkinById(skinId) }
                .onSuccess { skin ->
                    val inWatchlist = watchlistRepository.isInWatchlist(skinId)
                    _uiState.value = SkinDetailUiState.Success(skin, inWatchlist)
                    analytics.logSkinViewed(skin.id, skin.name)
                    loadSteamPrice(skin.marketHashName)
                }
                .onFailure { e ->
                    val messageRes = if (e is HttpException && e.code() == 404) {
                        R.string.skindetail_not_found
                    } else {
                        e.toUserMessageRes()
                    }
                    _uiState.value = SkinDetailUiState.Error(messageRes)
                }
        }
    }

    fun retry() = loadSkin()

    private fun loadSteamPrice(marketHashName: String) {
        // Own coroutine, launched only once the main skin data has rendered:
        // Steam is a live, per-device call (no backend/shared cache — see
        // SkinRepository.getSteamPrice) that can be slow or fail, and must
        // never hold up or corrupt the rest of the screen if it does.
        viewModelScope.launch {
            _steamSessionConnected.value = steamSession.isConnected()
            val price = runCatching { skinRepository.getSteamPrice(marketHashName) }.getOrNull()
            _steamPriceState.value = if (price != null) SteamPriceState.Available(price) else SteamPriceState.Unavailable
        }
    }

    /**
     * Called when the screen re-enters composition — i.e. on return from the
     * Steam login screen the hint navigates to. A price that failed without a
     * session is retried exactly on the disconnected→connected transition, so
     * the row fills in right where the user asked for it.
     */
    fun onScreenReentered() {
        val wasConnected = _steamSessionConnected.value
        val connectedNow = steamSession.isConnected()
        _steamSessionConnected.value = connectedNow
        if (connectedNow && !wasConnected && _steamPriceState.value is SteamPriceState.Unavailable) {
            val skin = (_uiState.value as? SkinDetailUiState.Success)?.skin ?: return
            _steamPriceState.value = SteamPriceState.Loading
            loadSteamPrice(skin.marketHashName)
        }
    }

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
