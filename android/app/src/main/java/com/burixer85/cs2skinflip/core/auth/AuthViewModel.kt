package com.burixer85.cs2skinflip.core.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthFormState(
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

/** Shared login/register logic used anywhere a sign-in prompt is shown (Alerts, Settings). */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _authState = MutableStateFlow(AuthFormState())
    val authState: StateFlow<AuthFormState> = _authState

    fun register(email: String, password: String, username: String?) {
        viewModelScope.launch {
            _authState.value = AuthFormState(submitting = true)
            val error = authRepository.register(email, password, username)
            _authState.value = AuthFormState(submitting = false, errorMessage = error)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthFormState(submitting = true)
            val error = authRepository.login(email, password)
            _authState.value = AuthFormState(submitting = false, errorMessage = error)
        }
    }

    fun resetFormState() {
        _authState.value = AuthFormState()
    }
}
