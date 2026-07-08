package com.burixer85.cs2skinflip.features.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
import com.burixer85.cs2skinflip.core.data.repository.BillingRepository
import com.burixer85.cs2skinflip.core.preferences.DefaultMarketplace
import com.burixer85.cs2skinflip.core.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val backendApi: CS2BackendApiService,
    private val billingRepository: BillingRepository,
) : ViewModel() {

    val marketplace: StateFlow<DefaultMarketplace> =
        preferences.marketplace.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultMarketplace.LOWEST)


    val isLoggedIn: StateFlow<Boolean> =
        authRepository.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _user = MutableStateFlow<MeResponseDto?>(null)
    val user: StateFlow<MeResponseDto?> = _user

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _user.value = if (loggedIn) runCatching { backendApi.getMe() }.getOrNull() else null
                // Safety net: covers a purchase that succeeded but never reached the backend
                // (e.g. app killed right after payment).
                if (loggedIn && billingRepository.syncPendingPurchases()) {
                    _user.value = runCatching { backendApi.getMe() }.getOrNull()
                }
            }
        }
        viewModelScope.launch {
            billingRepository.purchaseUpdates.collect { purchases ->
                var unlocked = false
                purchases.forEach { if (billingRepository.verify(it)) unlocked = true }
                if (unlocked) _user.value = runCatching { backendApi.getMe() }.getOrNull()
            }
        }
    }

    fun setMarketplace(m: DefaultMarketplace) {
        viewModelScope.launch { preferences.setMarketplace(m) }
    }

    fun purchasePremium(activity: Activity) {
        viewModelScope.launch { billingRepository.purchasePremium(activity) }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            _user.value = null
        }
    }

    private val _deleteAccountError = MutableStateFlow<String?>(null)
    val deleteAccountError: StateFlow<String?> = _deleteAccountError

    fun deleteAccount() {
        viewModelScope.launch {
            runCatching { authRepository.deleteAccount() }
                .onSuccess { _user.value = null }
                .onFailure { _deleteAccountError.value = "Couldn't delete your account. Please try again." }
        }
    }

    fun clearDeleteAccountError() {
        _deleteAccountError.value = null
    }
}
