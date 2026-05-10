package com.burixer85.cs2skinflip.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burixer85.cs2skinflip.core.auth.AuthRepository
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.MeResponseDto
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
            }
        }
    }

    fun setMarketplace(m: DefaultMarketplace) {
        viewModelScope.launch { preferences.setMarketplace(m) }
    }


    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            _user.value = null
        }
    }
}
