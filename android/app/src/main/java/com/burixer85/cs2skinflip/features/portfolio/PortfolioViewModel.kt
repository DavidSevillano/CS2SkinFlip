package com.burixer85.cs2skinflip.features.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.repository.PortfolioRepository
import com.burixer85.cs2skinflip.core.domain.model.Portfolio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PortfolioUiState {
    object NotConnected : PortfolioUiState()
    object Connecting : PortfolioUiState()
    object Loading : PortfolioUiState()
    data class Success(val portfolio: Portfolio) : PortfolioUiState()
    data class Error(val message: String) : PortfolioUiState()
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PortfolioUiState>(
        if (portfolioRepository.isConnected()) PortfolioUiState.Loading
        else PortfolioUiState.NotConnected
    )
    val uiState: StateFlow<PortfolioUiState> = _uiState

    init {
        if (portfolioRepository.isConnected()) loadPortfolio()
    }

    fun connectSteam() {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState.Connecting
            portfolioRepository.connectSteam()
                .catch { e -> _uiState.value = PortfolioUiState.Error(e.message ?: "Connection failed") }
                .collect { portfolio -> _uiState.value = PortfolioUiState.Success(portfolio) }
        }
    }

    private fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState.Loading
            portfolioRepository.getPortfolio()
                .catch { e -> _uiState.value = PortfolioUiState.Error(e.message ?: "Error") }
                .collect { portfolio ->
                    _uiState.value = if (portfolio != null)
                        PortfolioUiState.Success(portfolio)
                    else
                        PortfolioUiState.NotConnected
                }
        }
    }

    fun disconnect() {
        portfolioRepository.disconnect()
        _uiState.value = PortfolioUiState.NotConnected
    }
}
