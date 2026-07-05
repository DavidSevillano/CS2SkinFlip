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

data class ForgotPasswordState(
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val sent: Boolean = false,
)

data class ResendVerificationState(
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val sent: Boolean = false,
)

/** Shared login/register/password-recovery logic used anywhere a sign-in prompt is shown (Alerts, Settings). */
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

    private val _forgotPasswordState = MutableStateFlow(ForgotPasswordState())
    val forgotPasswordState: StateFlow<ForgotPasswordState> = _forgotPasswordState

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _forgotPasswordState.value = ForgotPasswordState(submitting = true)
            val error = authRepository.forgotPassword(email)
            _forgotPasswordState.value = if (error == null) {
                ForgotPasswordState(sent = true)
            } else {
                ForgotPasswordState(errorMessage = error)
            }
        }
    }

    fun resetForgotPasswordState() {
        _forgotPasswordState.value = ForgotPasswordState()
    }

    private val _resendVerificationState = MutableStateFlow(ResendVerificationState())
    val resendVerificationState: StateFlow<ResendVerificationState> = _resendVerificationState

    fun resendVerification() {
        viewModelScope.launch {
            _resendVerificationState.value = ResendVerificationState(submitting = true)
            val error = authRepository.resendVerification()
            _resendVerificationState.value = if (error == null) {
                ResendVerificationState(sent = true)
            } else {
                ResendVerificationState(errorMessage = error)
            }
        }
    }
}
