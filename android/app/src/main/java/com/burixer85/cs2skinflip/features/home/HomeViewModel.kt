package com.burixer85.cs2skinflip.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.domain.model.Skin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val trendingSkins: List<Skin>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val skinRepository: SkinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadTrending()
        // Apply live price corrections whenever the detail screen fetches real-time data
        viewModelScope.launch {
            skinRepository.livePriceCache.collect { cache ->
                val current = _uiState.value as? HomeUiState.Success ?: return@collect
                if (cache.isEmpty()) return@collect
                val updated = current.trendingSkins.map { skin ->
                    cache[skin.id]?.let { live ->
                        skin.copy(
                            cs2capPrice     = live.cs2capPrice,
                            csfloatPrice    = live.csfloatPrice,
                            csgoMarketPrice = live.csgoMarketPrice,
                            lowestPrice     = live.lowestPrice,
                            priceChange24h  = live.priceChange24h,
                        )
                    } ?: skin
                }
                if (updated != current.trendingSkins) {
                    _uiState.value = HomeUiState.Success(updated)
                }
            }
        }
    }

    fun loadTrending() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            skinRepository.getTrendingSkins()
                .catch { e -> _uiState.value = HomeUiState.Error(e.message ?: "Unknown error") }
                .collect { skins ->
                    // Fetch live prices before showing — cards display the correct DMarket
                    // exchange price from the first render, no flicker.
                    val liveSkins = skinRepository.refreshSkinPricesBatch(skins)
                    _uiState.value = HomeUiState.Success(liveSkins)
                }
        }
    }
}
