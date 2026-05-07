package com.burixer85.cs2skinflip.features.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.data.repository.AlertRepository
import com.burixer85.cs2skinflip.core.domain.model.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AlertsUiState {
    object Loading : AlertsUiState()
    data class Success(val alerts: List<Alert>, val isPremium: Boolean, val freeAlertsUsed: Int) : AlertsUiState()
    data class Error(val message: String) : AlertsUiState()
}

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertsUiState>(AlertsUiState.Loading)
    val uiState: StateFlow<AlertsUiState> = _uiState

    // Demo: not premium
    private val isPremium = false
    private val freeAlertLimit = 5

    init {
        viewModelScope.launch {
            if (alertRepository.countActive() == 0) {
                MockData.mockAlerts.forEach { alertRepository.add(it) }
            }
        }
        observeAlerts()
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            alertRepository.getAll()
                .catch { e -> _uiState.value = AlertsUiState.Error(e.message ?: "Error") }
                .collect { alerts ->
                    _uiState.value = AlertsUiState.Success(
                        alerts = alerts,
                        isPremium = isPremium,
                        freeAlertsUsed = alerts.count { it.isActive }
                    )
                }
        }
    }

    fun toggleAlert(alert: Alert) {
        viewModelScope.launch {
            alertRepository.setActive(alert, !alert.isActive)
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch { alertRepository.delete(id) }
    }
}
