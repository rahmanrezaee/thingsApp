package com.example.thingsappandroid.features.splash

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

sealed class SplashEffect {
    object NavigateToHome : SplashEffect()
    data class ShowError(val message: String) : SplashEffect()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    application: Application,
    private val repository: ThingsRepository,
    private val tokenManager: TokenManager
) : AndroidViewModel(application) {

    private val _effect = Channel<SplashEffect>()
    val effect = _effect.receiveAsFlow()

    private var appStartCheckJob: Job? = null

    /** Call when permissions are granted to run auth/location check. If a run is already in progress, it is cancelled and a new one is started. */
    fun runAppStartCheckIfNeeded() {
        appStartCheckJob?.cancel()
        val newJob = viewModelScope.launch {
            try {
                checkAppStart()
            } finally {
                if (appStartCheckJob == coroutineContext[Job]) appStartCheckJob = null
            }
        }
        appStartCheckJob = newJob
    }

    @SuppressLint("HardwareIds")
    private suspend fun checkAppStart() {
        delay(1000)

        val deviceId = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()

        val networkAvailable = NetworkUtils.isNetworkAvailable(getApplication())
        if (!networkAvailable) {
            Log.w("SplashViewModel", "No network connection available")
            tryNavigateToHomeOrRequestLocation()
            return
        }

        // 1. Use cached token if available
        val cachedToken = tokenManager.getToken()
        if (!cachedToken.isNullOrEmpty()) {
            Log.d("SplashViewModel", "Using cached token")
            NetworkModule.setAuthToken(cachedToken)
            tryNavigateToHomeOrRequestLocation()
            return
        }

        // 2. No token - register and get token only (no device info sync)
        try {
            Log.d("SplashViewModel", "Registering device and getting token for deviceId: $deviceId")
            val (success, token) = withTimeoutOrNull(15_000L) {
                repository.registerAndGetToken(getApplication(), deviceId)
            } ?: Pair(false, null)

            if (success && token != null) {
                Log.d("SplashViewModel", "✓ Registered and got token successfully")
                tryNavigateToHomeOrRequestLocation()
            } else {
                Log.e("SplashViewModel", "Register/get token failed")
                _effect.send(SplashEffect.ShowError("Failed to authenticate. Please check your internet connection and try again."))
                delay(2000)
                tryNavigateToHomeOrRequestLocation()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SplashViewModel", "Error during registration: ${e.message}", e)
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

    /** Navigate to home; we only require permission (grant/background), not "location services" enabled. */
    private suspend fun tryNavigateToHomeOrRequestLocation() {
        _effect.send(SplashEffect.NavigateToHome)
    }
}