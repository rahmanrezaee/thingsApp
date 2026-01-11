package com.example.thingsappandroid.features.home

import com.example.thingsappandroid.services.ClimateData

// 1. State
data class HomeState(
    val isLoading: Boolean = false,
    val isWifiConnected: Boolean = false,
    val batteryLevel: Float = 0f,
    val isCharging: Boolean = false,
    val deviceName: String = "",
    
    // Server/Calculated Data
    val carbonIntensity: Int = 0,
    val currentUsageKwh: Float = 0f,
    val avoidedEmissions: Float = 0f,
    val climateData: ClimateData? = null,
    
    val error: String? = null
)

// 2. Intents (Actions triggered by UI)
sealed class HomeIntent {
    object RefreshData : HomeIntent()
    object Initialize : HomeIntent()
    object Logout : HomeIntent()
}

// 3. Effects (One-off events like Navigation/Toast)
sealed class HomeEffect {
    data class ShowToast(val message: String) : HomeEffect()
    object NavigateToLogin : HomeEffect()
}