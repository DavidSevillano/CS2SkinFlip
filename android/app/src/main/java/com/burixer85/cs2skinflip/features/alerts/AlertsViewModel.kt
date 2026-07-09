package com.burixer85.cs2skinflip.features.alerts

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.analytics.AnalyticsService
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.repository.AlertRepository
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import com.burixer85.cs2skinflip.core.data.repository.SkinRepository
import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import com.burixer85.cs2skinflip.core.domain.model.Skin
import com.burixer85.cs2skinflip.core.util.toUserMessageRes
import com.burixer85.cs2skinflip.features.search.SearchFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

private fun Throwable.isUnauthorized(): Boolean = this is HttpException && code() == 401

sealed class AlertsUiState {
    object Loading : AlertsUiState()
    object NotLoggedIn : AlertsUiState()
    data class Success(
        val alerts: List<Alert>,
        val isPremium: Boolean,
        val freeLimit: Int?,  // null = unlimited
    ) : AlertsUiState() {
        /** True when the user is on free tier and at the alert cap */
        val atFreeLimit: Boolean get() = !isPremium && freeLimit != null && alerts.size >= freeLimit
    }
    data class Error(@StringRes val messageRes: Int) : AlertsUiState()
}

data class CreateAlertState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<Skin> = emptyList(),
    val selectedSkin: Skin? = null,
    val type: AlertType = AlertType.BUY_BELOW,
    val targetPrice: String = "",
    val submitting: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

data class EditAlertState(
    val alert: Alert? = null,           // non-null means the sheet is open
    val type: AlertType = AlertType.BUY_BELOW,
    val targetPrice: String = "",
    val submitting: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val authRepository: AuthRepository,
    private val skinRepository: SkinRepository,
    private val analytics: AnalyticsService,
    private val billingRepository: BillingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertsUiState>(AlertsUiState.Loading)
    val uiState: StateFlow<AlertsUiState> = _uiState

    private val _createState = MutableStateFlow(CreateAlertState())
    val createState: StateFlow<CreateAlertState> = _createState

    private val _editState = MutableStateFlow(EditAlertState())
    val editState: StateFlow<EditAlertState> = _editState

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    loadAlerts()
                    // Safety net: covers a purchase that succeeded but never reached the backend
                    // (e.g. app killed right after payment).
                    if (billingRepository.syncPendingPurchases()) loadAlerts()
                } else {
                    _uiState.value = AlertsUiState.NotLoggedIn
                }
            }
        }
        viewModelScope.launch {
            billingRepository.purchaseUpdates.collect { purchases ->
                var unlocked = false
                purchases.forEach { if (billingRepository.verify(it)) unlocked = true }
                if (unlocked) loadAlerts()
            }
        }
    }

    fun purchasePremium(activity: Activity) {
        viewModelScope.launch { billingRepository.purchasePremium(activity) }
    }

    fun loadAlerts() {
        viewModelScope.launch {
            _uiState.value = AlertsUiState.Loading
            runCatching { alertRepository.fetchAll() }
                .onSuccess { state ->
                    _uiState.value = AlertsUiState.Success(
                        alerts = state.alerts,
                        isPremium = state.isPremium,
                        freeLimit = state.freeLimit,
                    )
                }
                .onFailure { e ->
                    _uiState.value = if (e.isUnauthorized()) {
                        AlertsUiState.NotLoggedIn
                    } else {
                        AlertsUiState.Error(e.toUserMessageRes())
                    }
                }
        }
    }

    fun toggleAlert(alert: Alert) {
        viewModelScope.launch {
            runCatching { alertRepository.setActive(alert.id, !alert.isActive) }
                .onSuccess { loadAlerts() }
        }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch {
            analytics.logAlertDeleted(id)
            runCatching { alertRepository.delete(id) }
                .onSuccess { loadAlerts() }
        }
    }

    // ─── Edit alert flow ──────────────────────────────────────────────────────

    fun startEdit(alert: Alert) {
        _editState.value = EditAlertState(
            alert = alert,
            type = alert.type,
            targetPrice = "%.2f".format(alert.targetPrice),
        )
    }

    fun cancelEdit() { _editState.value = EditAlertState() }

    fun onEditTypeChange(type: AlertType) {
        _editState.update { it.copy(type = type) }
    }

    fun onEditTargetPriceChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' }
        _editState.update { it.copy(targetPrice = sanitized) }
    }

    /** Returns true on success — caller should dismiss the sheet. */
    suspend fun submitEdit(): Boolean {
        val s = _editState.value
        val alert = s.alert ?: return false
        val price = s.targetPrice.toDoubleOrNull()
        if (price == null || price <= 0) {
            _editState.update { it.copy(errorMessageRes = R.string.alerts_error_invalid_target_price) }
            return false
        }
        _editState.update { it.copy(submitting = true, errorMessageRes = null) }
        return runCatching {
            alertRepository.update(alert.id, type = s.type, targetPrice = price)
        }.fold(
            onSuccess = {
                analytics.logAlertEdited(alert.id, s.type.name, price)
                _editState.value = EditAlertState()
                loadAlerts()
                true
            },
            onFailure = { e ->
                if (e.isUnauthorized()) {
                    _uiState.value = AlertsUiState.NotLoggedIn
                    _editState.value = EditAlertState()
                } else {
                    _editState.update { it.copy(submitting = false, errorMessageRes = e.toUserMessageRes()) }
                }
                false
            },
        )
    }

    // ─── Create alert flow ────────────────────────────────────────────────────

    fun resetCreateState() { _createState.value = CreateAlertState() }

    fun onCreateQueryChange(q: String) {
        _createState.update { it.copy(query = q, selectedSkin = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _createState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)  // debounce
            _createState.update { it.copy(isSearching = true) }
            runCatching {
                skinRepository.searchSkinsPage(
                    query = q,
                    filters = SearchFilters(),
                    page = 1,
                    limit = 8,
                )
            }.onSuccess { (skins, _, _) ->
                _createState.update { it.copy(results = skins, isSearching = false) }
            }.onFailure {
                _createState.update { it.copy(results = emptyList(), isSearching = false) }
            }
        }
    }

    fun onCreateSkinSelected(skin: Skin) {
        _createState.update {
            it.copy(
                selectedSkin = skin,
                query = skin.name,
                results = emptyList(),
                targetPrice = if (skin.lowestMarketPrice > 0)
                    "%.2f".format(skin.lowestMarketPrice) else "",
            )
        }
    }

    fun onCreateTypeChange(type: AlertType) {
        _createState.update { it.copy(type = type) }
    }

    fun onCreateTargetPriceChange(value: String) {
        // Allow only digits and one decimal point
        val sanitized = value.filter { it.isDigit() || it == '.' }
        _createState.update { it.copy(targetPrice = sanitized) }
    }

    /** Returns true on success — caller should dismiss the sheet. */
    suspend fun submitCreateAlert(): Boolean {
        val s = _createState.value
        val skin = s.selectedSkin ?: run {
            _createState.update { it.copy(errorMessageRes = R.string.alerts_error_pick_skin_first) }
            return false
        }
        val typed = s.targetPrice.toDoubleOrNull()
        if (typed == null || typed <= 0) {
            _createState.update { it.copy(errorMessageRes = R.string.alerts_error_invalid_target_price) }
            return false
        }
        val priceUsd = typed  // always USD
        _createState.update { it.copy(submitting = true, errorMessageRes = null) }
        return runCatching {
            alertRepository.create(skin.id, s.type, priceUsd)
        }.fold(
            onSuccess = {
                analytics.logAlertCreated(skin.id, skin.name, s.type.name, priceUsd)
                resetCreateState()
                loadAlerts()
                true
            },
            onFailure = { e ->
                if (e.isUnauthorized()) {
                    _uiState.value = AlertsUiState.NotLoggedIn
                    resetCreateState()
                } else {
                    _createState.update {
                        it.copy(submitting = false, errorMessageRes = e.toUserMessageRes())
                    }
                }
                false
            },
        )
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
