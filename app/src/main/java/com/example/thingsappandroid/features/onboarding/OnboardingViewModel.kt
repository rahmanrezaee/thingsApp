package com.example.thingsappandroid.features.onboarding

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class OnboardingEffect {
    object NavigateToHome : OnboardingEffect()
    data class ShowError(val message: String) : OnboardingEffect()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val repository: ThingsRepository,
    private val tokenManager: TokenManager,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _effect = Channel<OnboardingEffect>(Channel.UNLIMITED)
    val effect = _effect.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @SuppressLint("HardwareIds")
    fun onGetStartedClicked() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val context = getApplication<Application>()
                
                // Check network connectivity first
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    Log.w("OnboardingViewModel", "No network connection available")
                    _effect.trySend(OnboardingEffect.ShowError("No internet connection. Please check your network settings and try again."))
                    _isLoading.value = false
                    return@launch
                }

                // Get Device ID
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString()

                Log.d("OnboardingViewModel", "Starting device registration for deviceId: $deviceId")

                // Save device ID
                tokenManager.saveDeviceId(deviceId)

                // Initialize device: register, get token, and sync device info (all in one call)
                Log.d("OnboardingViewModel", "Initializing device for deviceId: $deviceId")
                val (success, token, deviceInfo) = repository.initializeDevice(context, deviceId, null)
                
                if (!success || token == null) {
                    Log.e("OnboardingViewModel", "Device initialization failed")
                    _effect.trySend(OnboardingEffect.ShowError("Failed to initialize device. Please check your internet connection and try again."))
                    _isLoading.value = false
                    return@launch
                }
                
                // Save token
                tokenManager.saveToken(token)
                Log.d("OnboardingViewModel", "✓ Device initialized successfully")

                // 3. Mark onboarding as completed only if everything succeeded
                preferenceManager.setOnboardingCompleted(true)

                Log.d("OnboardingViewModel", "Device registration completed successfully")
                Log.d("OnboardingViewModel", "Sending NavigateToHome effect...")
                
                // Send effect before setting loading to false
                _effect.trySend(OnboardingEffect.NavigateToHome)
                Log.d("OnboardingViewModel", "NavigateToHome effect sent (trySend)")
                
                // Small delay to ensure effect is processed
                kotlinx.coroutines.delay(100)
                
                // Set loading to false after sending effect
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error during device registration: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("UnknownHostException") == true || 
                    e.message?.contains("Unable to resolve host") == true -> 
                        "Cannot connect to server. Please check your internet connection and try again."
                    e.message?.contains("timeout") == true || 
                    e.message?.contains("Timeout") == true -> 
                        "Connection timeout. Please check your internet connection and try again."
                    else -> "Failed to register device: ${e.message}. Please try again."
                }
                _effect.trySend(OnboardingEffect.ShowError(errorMessage))
                _isLoading.value = false
            }
        }
    }
}
