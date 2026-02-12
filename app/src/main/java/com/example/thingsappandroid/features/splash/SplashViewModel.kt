package com.example.thingsappandroid.features.splash

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.BatteryServiceActions
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

    fun runAppStartCheckIfNeeded() {
        appStartCheckJob?.cancel()
        appStartCheckJob = viewModelScope.launch {
            try {
                checkAppStart()
            } finally {
                if (appStartCheckJob == coroutineContext[Job]) appStartCheckJob = null
            }
        }
    }

    @SuppressLint("HardwareIds")
    private suspend fun checkAppStart() {
        delay(1000)

        val app = getApplication<Application>()
        val deviceId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()

        if (!NetworkUtils.isNetworkAvailable(app)) {
            Log.w(TAG, "No network connection available")
            applyCachedTokenAndNavigate()
            return
        }

        val cachedToken = tokenManager.getToken()
        try {
            Log.d(TAG, "Registering device and getting token for deviceId: $deviceId")
            val (success, token) = withTimeoutOrNull(15_000L) {
                repository.registerAndGetToken(app, deviceId)
            } ?: Pair(false, null)

            when {
                success && token != null -> {
                    Log.d(TAG, "Registered and got token successfully")
                    NetworkModule.setAuthToken(token)
                    if (cachedToken.isNullOrEmpty()) {
                        app.sendBroadcast(
                            Intent(BatteryServiceActions.FOR_NEW_DEVICE_CALL_CLIMATE_STATUS).apply {
                                setPackage(app.packageName)
                            }
                        )
                    } else {
                        app.sendBroadcast(
                            Intent(BatteryServiceActions.REQUEST_GET_DEVICE_INFO).apply {
                                setPackage(app.packageName)
                            }
                        )
                    }
                    navigateToHome()
                }

                !cachedToken.isNullOrEmpty() -> {
                    NetworkModule.setAuthToken(cachedToken)
                    navigateToHome()
                }

                else -> {
                    showErrorAndNavigate("Failed to authenticate. Please check your internet connection and try again.")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error during registration: ${e.message}", e)
            if (!cachedToken.isNullOrEmpty()) {
                NetworkModule.setAuthToken(cachedToken)
                navigateToHome()
            } else {
                showErrorAndNavigate(connectionErrorMessage(e))
            }
        }
    }

    private suspend fun applyCachedTokenAndNavigate() {
        tokenManager.getToken()?.takeIf { it.isNotBlank() }?.let { NetworkModule.setAuthToken(it) }
        navigateToHome()
    }

    private suspend fun showErrorAndNavigate(message: String) {
        _effect.send(SplashEffect.ShowError(message))
        delay(2000)
        navigateToHome()
    }

    private suspend fun navigateToHome() {
        _effect.send(SplashEffect.NavigateToHome)
    }

    private fun connectionErrorMessage(e: Exception): String = when {
        e.message?.contains("UnknownHostException") == true ||
                e.message?.contains("Unable to resolve host") == true ->
            "Cannot connect to server. Please check your internet connection."

        e.message?.contains("timeout") == true ->
            "Connection timeout. Please try again."

        else ->
            "Failed to connect. Please check your internet connection."
    }

    companion object {
        private const val TAG = "SplashViewModel"
    }
}