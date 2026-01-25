package com.example.thingsappandroid.features.auth.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.annotation.SuppressLint
import android.provider.Settings
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AuthorizeUiState(
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isInitialized: Boolean = false
)

class AuthorizeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ThingsRepository()
    private val tokenManager = TokenManager(application)
    
    private val _uiState = MutableStateFlow(AuthorizeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        initialize()
    }

    @SuppressLint("HardwareIds")
    private fun initialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitializing = true, error = null) }
            
            try {
                // 1. Ensure Device ID
                val deviceId = Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString()

                // 2. Ensure Authentication
                var token = tokenManager.getToken()
                if (token == null) {
                    token = repository.authenticate(deviceId)
                    if (token != null) {
                        tokenManager.saveToken(token)
                        NetworkModule.setAuthToken(token)
                    }
                } else {
                    NetworkModule.setAuthToken(token)
                }

                // 3. Sync/Load Data
                if (token != null) {
                    repository.syncDeviceInfo(context = getApplication(), deviceId = deviceId, stationCode = null)
                    _uiState.update { it.copy(isInitializing = false, isInitialized = true) }
                } else {
                    _uiState.update { it.copy(isInitializing = false, error = "Connection failed. Please ensure you are online.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isInitializing = false, error = "Initialization error: ${e.message}") }
            }
        }
    }

    @SuppressLint("HardwareIds")
    fun authorize(sessionId: String, requestedBy: String, requestedUrl: String) {
        // Prevent multiple simultaneous authorization attempts
        if (_uiState.value.isLoading) {
            android.util.Log.d("AuthorizeViewModel", "Authorization skipped: already loading")
            return
        }

        android.util.Log.d("AuthorizeViewModel", "=== Authorization Request Started ===")
        android.util.Log.d("AuthorizeViewModel", "SessionId: $sessionId, RequestedBy: $requestedBy, RequestedUrl: $requestedUrl")
        android.util.Log.d("AuthorizeViewModel", "Initialization state: isInitializing=${_uiState.value.isInitializing}, isInitialized=${_uiState.value.isInitialized}")

        viewModelScope.launch {
            // Set loading state immediately to disable button
            _uiState.update { it.copy(isLoading = true, error = null, isSuccess = false) }
            
            try {
                // Ensure we have a token before proceeding
                var token = tokenManager.getToken()
                val deviceId = Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString()
                
                // If no token, get one immediately (don't wait for initialization)
                if (token == null) {
                    android.util.Log.d("AuthorizeViewModel", "No token found, authenticating now...")
                    token = repository.authenticate(deviceId)
                    if (token != null) {
                        tokenManager.saveToken(token)
                        NetworkModule.setAuthToken(token)
                        android.util.Log.d("AuthorizeViewModel", "Token obtained and set")
                    } else {
                        android.util.Log.e("AuthorizeViewModel", "Failed to get token")
                        _uiState.update { it.copy(isLoading = false, error = "Authentication failed. Please check your connection and try again.") }
                        return@launch
                    }
                } else {
                    // Ensure token is set in NetworkModule (might not be set if initialization is still running)
                    NetworkModule.setAuthToken(token)
                    android.util.Log.d("AuthorizeViewModel", "Using existing token")
                }

                // Now proceed with authorization
                android.util.Log.d("AuthorizeViewModel", "Calling repository.authorizeGreenLogin()...")
                val success = repository.authorizeGreenLogin(
                    deviceId = deviceId,
                    sessionId = sessionId,
                    requestedBy = requestedBy,
                    requestedUrl = requestedUrl
                )

                android.util.Log.d("AuthorizeViewModel", "Authorization API call completed. Success: $success")
                
                // Wait a moment to ensure state is updated
                kotlinx.coroutines.delay(100)
                
                if (success) {
                    android.util.Log.d("AuthorizeViewModel", "✅ Authorization successful!")
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                } else {
                    android.util.Log.e("AuthorizeViewModel", "❌ Authorization failed")
                    _uiState.update { it.copy(isLoading = false, error = "Authorization failed. Please try again.") }
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthorizeViewModel", "Exception during authorization: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = "Error: ${e.message ?: "Unknown error occurred"}") }
            }
        }
    }
    
    fun resetState() {
        _uiState.update { AuthorizeUiState() }
        initialize()
    }

    fun verifyStationCode(code: String) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isSuccess = false) }

            try {
                val deviceId = Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString()

                val result = repository.setStation(deviceId, code)
                
                if (result.first) {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                    // Re-sync device info to reflect changes in the app
                    repository.syncDeviceInfo(getApplication(), deviceId, null)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = result.second ?: "Failed to verify station code") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Error: ${e.message}") }
            }
        }
    }
}