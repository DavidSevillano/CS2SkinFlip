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
import com.burixer85.cs2skinflip.core.steam.SteamSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [Loading] covers both "auth state not checked yet" and "logged in but profile still
 * fetching" — collapsing those into [SignedOut] is what caused the Settings screen to
 * flash the "Sign in with Steam" prompt before the real account loaded.
 */
sealed class AccountUiState {
    object Loading : AccountUiState()
    object SignedOut : AccountUiState()
    data class SignedIn(val user: MeResponseDto) : AccountUiState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val backendApi: CS2BackendApiService,
    private val billingRepository: BillingRepository,
    private val steamSession: SteamSessionManager,
) : ViewModel() {

    val marketplace: StateFlow<DefaultMarketplace> =
        preferences.marketplace.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultMarketplace.LOWEST)

    private val _steamSessionConnected = MutableStateFlow(steamSession.isConnected())
    val steamSessionConnected: StateFlow<Boolean> = _steamSessionConnected

    /** Re-reads the cookie jar — call when Settings regains focus after the login screen. */
    fun refreshSteamSession() {
        _steamSessionConnected.value = steamSession.isConnected()
    }

    fun disconnectSteamSession() {
        steamSession.disconnect()
        refreshSteamSession()
    }

    private val _accountState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val accountState: StateFlow<AccountUiState> = _accountState

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (!loggedIn) {
                    _accountState.value = AccountUiState.SignedOut
                    return@collect
                }
                val me = runCatching { backendApi.getMe() }.getOrNull()
                _accountState.value = me?.let { AccountUiState.SignedIn(it) } ?: AccountUiState.SignedOut
                // Safety net: covers a purchase that succeeded but never reached the backend
                // (e.g. app killed right after payment).
                if (billingRepository.syncPendingPurchases()) {
                    val refreshed = runCatching { backendApi.getMe() }.getOrNull()
                    if (refreshed != null) _accountState.value = AccountUiState.SignedIn(refreshed)
                }
            }
        }
        viewModelScope.launch {
            billingRepository.purchaseUpdates.collect { purchases ->
                var unlocked = false
                purchases.forEach { if (billingRepository.verify(it)) unlocked = true }
                if (unlocked) {
                    val refreshed = runCatching { backendApi.getMe() }.getOrNull()
                    if (refreshed != null) _accountState.value = AccountUiState.SignedIn(refreshed)
                }
            }
        }
    }

    fun setMarketplace(m: DefaultMarketplace) {
        viewModelScope.launch { preferences.setMarketplace(m) }
    }

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError

    fun purchasePremium(activity: Activity) {
        viewModelScope.launch {
            val launched = billingRepository.purchasePremium(activity)
            if (!launched) {
                _purchaseError.value = "Couldn't start the purchase. Check your connection and try again."
            }
        }
    }

    fun clearPurchaseError() {
        _purchaseError.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            // The Steam web session is the same person's Steam identity, so signing
            // out of the account clears it too — otherwise the row still read
            // "Conectada" after sign-out, and the cookies would outlive the account
            // on a shared device.
            steamSession.disconnect()
            _steamSessionConnected.value = false
            _accountState.value = AccountUiState.SignedOut
        }
    }

    private val _deleteAccountError = MutableStateFlow<String?>(null)
    val deleteAccountError: StateFlow<String?> = _deleteAccountError

    fun deleteAccount() {
        viewModelScope.launch {
            runCatching { authRepository.deleteAccount() }
                .onSuccess { _accountState.value = AccountUiState.SignedOut }
                .onFailure { _deleteAccountError.value = "Couldn't delete your account. Please try again." }
        }
    }

    fun clearDeleteAccountError() {
        _deleteAccountError.value = null
    }
}
