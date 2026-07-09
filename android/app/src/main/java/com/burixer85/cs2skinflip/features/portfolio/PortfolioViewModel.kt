package com.burixer85.cs2skinflip.features.portfolio

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.remote.SteamInventoryPrivateException
import com.burixer85.cs2skinflip.core.data.repository.PortfolioRepository
import com.burixer85.cs2skinflip.core.domain.model.PortfolioItem
import com.burixer85.cs2skinflip.core.domain.model.PortfolioSummary
import com.burixer85.cs2skinflip.core.util.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

private fun Throwable.isUnauthorized(): Boolean = this is HttpException && code() == 401
private fun Throwable.isBadRequest(): Boolean = this is HttpException && code() == 400

sealed class PortfolioUiState {
    object Loading : PortfolioUiState()
    object NotLoggedIn : PortfolioUiState()
    data class Success(
        val items: List<PortfolioItem>,
        val summary: PortfolioSummary,
        val syncing: Boolean = false,
        @StringRes val syncErrorRes: Int? = null,
    ) : PortfolioUiState()
    data class Error(@StringRes val messageRes: Int) : PortfolioUiState()
}

data class EditPriceState(
    val item: PortfolioItem? = null,   // non-null means the dialog is open
    val price: String = "",
    val submitting: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val uiState: StateFlow<PortfolioUiState> = _uiState

    private val _editState = MutableStateFlow(EditPriceState())
    val editState: StateFlow<EditPriceState> = _editState

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) load() else _uiState.value = PortfolioUiState.NotLoggedIn
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState.Loading
            runCatching { portfolioRepository.fetchAll() }
                .onSuccess { state ->
                    _uiState.value = PortfolioUiState.Success(items = state.items, summary = state.summary)
                }
                .onFailure { e ->
                    _uiState.value = if (e.isUnauthorized()) PortfolioUiState.NotLoggedIn
                    else PortfolioUiState.Error(e.toUserMessageRes())
                }
        }
    }

    fun sync() {
        val current = _uiState.value as? PortfolioUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(syncing = true, syncErrorRes = null)
            runCatching { portfolioRepository.sync() }
                .onSuccess { load() }
                .onFailure { e ->
                    val messageRes = when {
                        e is SteamInventoryPrivateException -> R.string.portfolio_error_inventory_private
                        e.isBadRequest() -> R.string.portfolio_error_sync_failed
                        else -> e.toUserMessageRes()
                    }
                    _uiState.value = current.copy(syncing = false, syncErrorRes = messageRes)
                }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            runCatching { portfolioRepository.deleteItem(id) }
                .onSuccess { load() }
        }
    }

    // ─── Edit price flow ──────────────────────────────────────────────────────

    fun startEdit(item: PortfolioItem) {
        _editState.value = EditPriceState(item = item, price = "%.2f".format(item.acquirePrice))
    }

    fun cancelEdit() { _editState.value = EditPriceState() }

    fun onEditPriceChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' }
        _editState.update { it.copy(price = sanitized) }
    }

    /** Returns true on success — caller should dismiss the dialog. */
    suspend fun submitEditPrice(): Boolean {
        val s = _editState.value
        val item = s.item ?: return false
        val price = s.price.toDoubleOrNull()
        if (price == null || price <= 0) {
            _editState.update { it.copy(errorMessageRes = R.string.portfolio_error_invalid_price) }
            return false
        }
        _editState.update { it.copy(submitting = true, errorMessageRes = null) }
        return runCatching {
            portfolioRepository.updateAcquirePrice(item.skinId, item.assetId, price)
        }.fold(
            onSuccess = {
                _editState.value = EditPriceState()
                load()
                true
            },
            onFailure = { e ->
                if (e.isUnauthorized()) {
                    _uiState.value = PortfolioUiState.NotLoggedIn
                    _editState.value = EditPriceState()
                } else {
                    _editState.update { it.copy(submitting = false, errorMessageRes = e.toUserMessageRes()) }
                }
                false
            },
        )
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
