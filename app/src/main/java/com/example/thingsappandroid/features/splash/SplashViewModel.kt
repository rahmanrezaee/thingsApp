package com.example.thingsappandroid.features.splash

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.services.BatteryServiceActions
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
    data class ShowError(val message: String) : SplashEffect()
    object RequestEnableLocation : SplashEffect()
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

    private var appStartCheckStarted = false

    /** Call when permissions are granted to run auth/location check. Only runs once. */
    fun runAppStartCheckIfNeeded() {
        if (appStartCheckStarted) return
        appStartCheckStarted = true
        checkAppStart()
    }

    @SuppressLint("HardwareIds")
    private fun checkAppStart() {
        viewModelScope.launch {
            delay(1000)

            // Get Device ID (onboarding already completed before reaching splash)
            val deviceId = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()

            // Check network connectivity first
            if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                Log.w("SplashViewModel", "No network connection available - will use last saved device info if any")
                tryNavigateToHomeOrRequestLocation()
                return@launch
            }

            // Always register device and get token (no login required)
            // 1. Check Local Token First
            val cachedToken = tokenManager.getToken()
            if (!cachedToken.isNullOrEmpty()) {
                Log.d("SplashViewModel", "Using cached token")
                Log.d("SplashViewModel", "deviceId: $deviceId")
                NetworkModule.setAuthToken(cachedToken)
                var lastDeviceInfo = repository.getLastDeviceInfo()
                if (lastDeviceInfo == null) {
                    lastDeviceInfo = repository.syncDeviceInfo(getApplication(), deviceId, null)
                }
                val hasStation = lastDeviceInfo?.stationInfo != null
                preferenceManager.setHasStation(hasStation)
                getApplication<Application>().sendBroadcast(Intent(BatteryServiceActions.HAS_STATION_UPDATED))
                tryNavigateToHomeOrRequestLocation()
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
                    deviceInfo?.let { preferenceManager.saveLastDeviceInfo(it) }
                    val hasStation = deviceInfo?.stationInfo != null
                    preferenceManager.setHasStation(hasStation)
                    getApplication<Application>().sendBroadcast(Intent(BatteryServiceActions.HAS_STATION_UPDATED))
                    tryNavigateToHomeOrRequestLocation()
                } else {
                    Log.e("SplashViewModel", "Device initialization failed")
                    _effect.send(SplashEffect.ShowError("Failed to authenticate. Please check your internet connection and try again."))
                    delay(2000)
                    tryNavigateToHomeOrRequestLocation()
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
                tryNavigateToHomeOrRequestLocation()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** If location is off and user hasn't skipped, send RequestEnableLocation; otherwise NavigateToHome. */
    private suspend fun tryNavigateToHomeOrRequestLocation() {
        if (isLocationEnabled()) {
            _effect.send(SplashEffect.NavigateToHome)
            return
        }
        if (preferenceManager.getLocationRequestSkipped()) {
            _effect.send(SplashEffect.NavigateToHome)
            return
        }
        _effect.send(SplashEffect.RequestEnableLocation)
    }

    fun skipLocationAndNavigateHome() {
        preferenceManager.setLocationRequestSkipped(true)
        viewModelScope.launch {
            _effect.send(SplashEffect.NavigateToHome)
        }
    }
}