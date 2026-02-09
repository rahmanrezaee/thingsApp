package com.example.thingsappandroid.features.auth.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.ui.components.showErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.thingsappandroid.ui.components.showInfoMessage
import com.example.thingsappandroid.ui.components.showSuccessMessage
import io.sentry.Sentry
import io.sentry.Breadcrumb
import io.sentry.protocol.User
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val repository: ThingsRepository
) : AndroidViewModel(application) {

    // Dependencies
    private val tokenManager = TokenManager(application)

    // MVI State
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    // MVI Effect
    private val _effect = Channel<AuthEffect>()
    val effect = _effect.receiveAsFlow()

    fun dispatch(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.UpdateEmail -> updateEmail(intent.email)
            is AuthIntent.UpdatePassword -> updatePassword(intent.password)
            is AuthIntent.TogglePasswordVisibility -> togglePasswordVisibility()
            is AuthIntent.Login -> performLogin()
            is AuthIntent.GuestLogin -> performGuestLogin()
            is AuthIntent.GoogleLogin -> performGoogleLogin()
            is AuthIntent.FacebookLogin -> performFacebookLogin()
            is AuthIntent.NavigateToSignUp -> navigateToSignUp()
            is AuthIntent.NavigateToForgotPassword -> navigateToForgotPassword()
        }
    }

    private fun updateEmail(email: String) {
        _state.value = _state.value.copy(email = email)
    }

    private fun updatePassword(password: String) {
        _state.value = _state.value.copy(password = password)
    }

    private fun togglePasswordVisibility() {
        _state.value = _state.value.copy(isPasswordVisible = !_state.value.isPasswordVisible)
    }

    private fun performLogin() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoginLoading = true, error = null)

            // TODO: Implement actual login with email/password
            // For now, just show a message
            showInfoMessage("Login functionality coming soon!")

            _state.value = _state.value.copy(isLoginLoading = false)
        }
    }

    @SuppressLint("HardwareIds")
    private fun performGuestLogin() {
        _state.value = _state.value.copy(isGoogleLoading = true, error = null)

        // TODO: Implement Google OAuth login
        showInfoMessage("performGuestLogin coming soon!")

        _state.value = _state.value.copy(isGoogleLoading = false)
    }

    private fun performGoogleLogin() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGoogleLoading = true, error = null)

            // TODO: Implement Google OAuth login
            showInfoMessage("Google login coming soon!")

            _state.value = _state.value.copy(isGoogleLoading = false)
        }
    }

    private fun performFacebookLogin() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isFacebookLoading = true, error = null)

            // TODO: Implement Facebook OAuth login
            showInfoMessage("Facebook login coming soon!")

            _state.value = _state.value.copy(isFacebookLoading = false)
        }
    }

    private fun navigateToSignUp() {
        viewModelScope.launch {
            _effect.send(AuthEffect.NavigateToSignUp)
        }
    }

    private fun navigateToForgotPassword() {
        viewModelScope.launch {
            _effect.send(AuthEffect.NavigateToForgotPassword)
        }
    }
}