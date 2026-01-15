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

            // Always register device and get token (no login required)
            // 1. Check Local Token First
            val cachedToken = tokenManager.getToken()
            if (!cachedToken.isNullOrEmpty()) {
                // Token exists, use it
                Log.d("SplashViewModel", "Using cached token")
                Log.d("SplashViewModel", "deviceId: $deviceId")
                NetworkModule.setAuthToken(cachedToken)
                
                // Still register device to ensure it's synced
                repository.syncDeviceInfo(deviceId)
                
                _effect.send(SplashEffect.NavigateToHome)
                return@launch
            }
            
            // 2. No token - authenticate to get token (device registration happens automatically)
            val token = repository.authenticate(deviceId)
            if (token != null) {
                tokenManager.saveToken(token)
                NetworkModule.setAuthToken(token)
                
                // Register device after getting token
                repository.syncDeviceInfo(deviceId)
                
                _effect.send(SplashEffect.NavigateToHome)
            } else {
                // If authentication fails, still try to go to home (device can work offline)
                Log.w("SplashViewModel", "Authentication failed, proceeding to home anyway")
                _effect.send(SplashEffect.NavigateToHome)
            }
        }
    }
}