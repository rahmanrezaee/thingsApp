package com.example.thingsappandroid.features.activity.viewModel

import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.services.ClimateData

// 1. State
data class ActivityState(
    val isLoading: Boolean = false,
    val isWifiConnected: Boolean = false,
    val batteryLevel: Float = 0f,
    val isCharging: Boolean = false,
    val deviceName: String = "",

    // Device Info from API
    val deviceInfo: DeviceInfoResponse? = null,

    // Server/Calculated Data
    val carbonIntensity: Int = 0,
    val currentUsageKwh: Float = 0f, // Live/Session usage
    val totalConsumedKwh: Float = 0f, // Historical/Server total
    val avoidedEmissions: Float = 0f,
    val climateData: ClimateData? = null,

    val stationName: String? = null,
    val isGreenStation: Boolean = false,

    val error: String? = null
)

// 2. Intents (Actions triggered by UI)
sealed class ActivityIntent {
    object RefreshData : ActivityIntent()
    object Initialize : ActivityIntent()
    object Logout : ActivityIntent()
}

// 3. Effects (One-off events like Navigation/Toast)
sealed class ActivityEffect {
    data class ShowToast(val message: String) : ActivityEffect()
    object NavigateToLogin : ActivityEffect()
}