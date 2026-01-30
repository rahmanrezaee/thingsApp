package com.example.thingsappandroid.features.splash

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class SplashEffect {
    object NavigateToHome : SplashEffect()
    object NavigateToOnboarding : SplashEffect()
    data class ShowError(val message: String) : SplashEffect()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    application: Application,
    private val repository: ThingsRepository,
    private val tokenManager: TokenManager,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _effect = Channel<SplashEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        checkAppStart()
    }

    @SuppressLint("HardwareIds")
    private fun checkAppStart() {
        viewModelScope.launch {
            // Artificial delay for branding (optional, remove if speed is critical)
            delay(1000)

            if (!preferenceManager.isOnboardingCompleted()) {
                _effect.send(SplashEffect.NavigateToOnboarding)
                return@launch
            }

            // Get Device ID
            val deviceId = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()

            // Check network connectivity first
            if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                Log.w("SplashViewModel", "No network connection available")
                _effect.send(SplashEffect.ShowError("No internet connection. Please check your network settings."))
                // Still navigate to home - user can retry later
                delay(2000) // Show error message briefly
                _effect.send(SplashEffect.NavigateToHome)
                return@launch
            }

            // Always register device and get token (no login required)
            // 1. Check Local Token First
            val cachedToken = tokenManager.getToken()
            if (!cachedToken.isNullOrEmpty()) {
                // Token exists, use it
                Log.d("SplashViewModel", "Using cached token")
                Log.d("SplashViewModel", "deviceId: $deviceId")
                NetworkModule.setAuthToken(cachedToken)
                
                // Still register device to ensure it's synced (with network check)
                try {
                    val deviceInfo = repository.syncDeviceInfo(getApplication(), deviceId, null)
                    val hasStation = deviceInfo?.stationInfo != null
                    preferenceManager.setHasStation(hasStation)
                    if (hasStation) {
                        getApplication<Application>().sendBroadcast(
                            android.content.Intent("com.example.thingsappandroid.HAS_STATION_UPDATED")
                        )
                    }
                } catch (e: Exception) {
                    Log.w("SplashViewModel", "Failed to sync device info: ${e.message}")
                    // Continue anyway - token is valid
                }
                
                _effect.send(SplashEffect.NavigateToHome)
                return@launch
            }
            
            // 2. No token - initialize device (register, get token, sync info)
            try {
                Log.d("SplashViewModel", "Initializing device for deviceId: $deviceId")
                val (success, token, deviceInfo) = repository.initializeDevice(getApplication(), deviceId, null)
                
                if (success && token != null) {
                    Log.d("SplashViewModel", "✓ Device initialized successfully")
                    tokenManager.saveToken(token)
                    NetworkModule.setAuthToken(token)
                    val hasStation = deviceInfo?.stationInfo != null
                    preferenceManager.setHasStation(hasStation)
                    if (hasStation) {
                        getApplication<Application>().sendBroadcast(
                            android.content.Intent("com.example.thingsappandroid.HAS_STATION_UPDATED")
                        )
                    }
                    _effect.send(SplashEffect.NavigateToHome)
                } else {
                    Log.e("SplashViewModel", "Device initialization failed")
                    _effect.send(SplashEffect.ShowError("Failed to authenticate. Please check your internet connection and try again."))
                    delay(2000)
                    _effect.send(SplashEffect.NavigateToHome)
                }
            } catch (e: Exception) {
                Log.e("SplashViewModel", "Error during initialization: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("UnknownHostException") == true || 
                    e.message?.contains("Unable to resolve host") == true -> 
                        "Cannot connect to server. Please check your internet connection."
                    e.message?.contains("timeout") == true -> 
                        "Connection timeout. Please try again."
                    else -> 
                        "Failed to connect. Please check your internet connection."
                }
                _effect.send(SplashEffect.ShowError(errorMessage))
                delay(2000)
                _effect.send(SplashEffect.NavigateToHome)
            }
        }
    }
}