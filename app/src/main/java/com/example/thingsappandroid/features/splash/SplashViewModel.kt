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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class SplashEffect {
    object NavigateToHome : SplashEffect()
    object NavigateToLogin : SplashEffect()
    object NavigateToOnboarding : SplashEffect()
}

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ThingsRepository()
    private val tokenManager = TokenManager(application)
    private val preferenceManager = PreferenceManager(application)

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

            // 1. Check Local Token First
            val cachedToken = tokenManager.getToken()
            if (!cachedToken.isNullOrEmpty()) {

                // log the token for debugging purposes
                Log.d("SplashViewModel", "Cached Token: $cachedToken")
                Log.d("SplashViewModel", "deviceId: $deviceId")

                NetworkModule.setAuthToken(cachedToken)
                _effect.send(SplashEffect.NavigateToHome)
                return@launch
            }
            val token = repository.authenticate(deviceId)

            if (token != null) {
                tokenManager.saveToken(token)
                NetworkModule.setAuthToken(token)
                _effect.send(SplashEffect.NavigateToHome)
            } else {
                _effect.send(SplashEffect.NavigateToLogin)
            }
        }
    }
}