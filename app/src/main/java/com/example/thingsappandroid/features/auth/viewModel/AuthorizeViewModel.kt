package com.example.thingsappandroid.features.auth.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.annotation.SuppressLint
import android.provider.Settings
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AuthorizeUiState(
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isInitialized: Boolean = false,
    val climateStatus: Int? = null
)

@HiltViewModel
class AuthorizeViewModel @Inject constructor(
    application: Application,
    private val repository: ThingsRepository
) : AndroidViewModel(application) {
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
                android.util.Log.d("AuthorizeViewModel", "=== Initialization Started ===")
                
                // 1. Ensure Device ID
                val deviceId = Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString()
                android.util.Log.d("AuthorizeViewModel", "Device ID: $deviceId")

                // 2. Ensure Authentication
                var token = tokenManager.getToken()
                if (token == null) {
                    android.util.Log.d("AuthorizeViewModel", "No token found, authenticating...")
                    token = repository.authenticate(deviceId)
                    if (token != null) {
                        tokenManager.saveToken(token)
                        NetworkModule.setAuthToken(token)
                        android.util.Log.d("AuthorizeViewModel", "Token obtained and saved")
                    } else {
                        android.util.Log.e("AuthorizeViewModel", "Failed to authenticate")
                    }
                } else {
                    NetworkModule.setAuthToken(token)
                    android.util.Log.d("AuthorizeViewModel", "Using existing token")
                }

                // 3. Fetch Device Info from Backend
                if (token != null) {
                    // Load cached info first for immediate display
                    val cachedInfo = repository.getLastDeviceInfo()
                    android.util.Log.d("AuthorizeViewModel", "Cached climate status: ${cachedInfo?.climateStatus}")
                    if (cachedInfo?.climateStatus != null) {
                        _uiState.update { it.copy(climateStatus = cachedInfo.climateStatus) }
                    }

                    // Fetch fresh data from backend
                    android.util.Log.d("AuthorizeViewModel", "Fetching device info from backend...")
                    val updatedInfo = repository.syncDeviceInfo(
                        context = getApplication(), 
                        deviceId = deviceId, 
                        stationCode = null
                    )
                    
                    android.util.Log.d("AuthorizeViewModel", "Backend response - climate status: ${updatedInfo?.climateStatus}")
                    
                    _uiState.update { 
                        it.copy(
                            isInitializing = false, 
                            isInitialized = true,
                            climateStatus = updatedInfo?.climateStatus ?: it.climateStatus
                        ) 
                    }
                    android.util.Log.d("AuthorizeViewModel", "✅ Initialization complete. Final climate status: ${_uiState.value.climateStatus}")
                } else {
                    android.util.Log.e("AuthorizeViewModel", "❌ No token available")
                    _uiState.update { it.copy(isInitializing = false, error = "Connection failed. Please ensure you are online.") }
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthorizeViewModel", "❌ Initialization error: ${e.message}", e)
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

    /** Station code is set only from Home → StationBottomSheet, not from this screen. */
    fun verifyStationCode(code: String) {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(
                isLoading = false,
                error = "To set your station code, use the Station option on the Home screen."
            )
        }
    }
}