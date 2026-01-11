package com.example.thingsappandroid.features.auth

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.ui.components.showErrorMessage
import com.example.thingsappandroid.ui.components.showInfoMessage
import com.example.thingsappandroid.ui.components.showSuccessMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val repository = ThingsRepository()
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isGuestLoading = true, error = null)

            try {
                // Generate or retrieve Device ID
                val deviceId = Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID)
                    ?: UUID.randomUUID().toString()

                // Step 1: Register the device first (all fields filled)
                val deviceInfo = repository.syncDeviceInfo(deviceId)

                if (deviceInfo != null) {
                    // Step 2: Get authentication token
                    val token = repository.authenticate(deviceId)

                    if (token != null) {
                        // Save token in "secure format" (Private Mode SharedPreferences)
                        tokenManager.saveToken(token)

                        showSuccessMessage("Successfully connected to Things App!")
                        _effect.send(AuthEffect.NavigateToHome)
                    } else {
                        showErrorMessage("Failed to get authentication token. Please check your connection.")
                        // Logout logic: Clear any potential partial state
                        tokenManager.clearToken()
                    }
                } else {
                    showErrorMessage("Failed to register device. Please check your connection.")
                    // Logout logic: Clear any potential partial state
                    tokenManager.clearToken()
                }
            } catch (e: Exception) {
                showErrorMessage("Connection failed. Please check your internet connection.")
                // Logout logic: Clear any potential partial state
                tokenManager.clearToken()
            }

            _state.value = _state.value.copy(isGuestLoading = false)
        }
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