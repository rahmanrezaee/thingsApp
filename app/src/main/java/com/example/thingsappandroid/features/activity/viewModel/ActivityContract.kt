package com.example.thingsappandroid.features.activity.viewModel

import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.data.model.StationInfo
import com.example.thingsappandroid.util.ClimateData

// 1. State
data class ActivityState(
    val isLoading: Boolean = false,
    val batteryLevel: Float = 0f,
    val isCharging: Boolean = false,
    val batteryCapacityMwh: Int? = null, // Battery capacity in mWh
    val deviceName: String = "",

    // Device Info from API
    val deviceInfo: DeviceInfoResponse? = null,

    // Server/Calculated Data
    val carbonIntensity: Int = 0,
    val currentUsageKwh: Float = 0f, // Live/Session usage
    val totalConsumedKwh: Float = 0f, // Historical/Server total
    val avoidedEmissions: Float = 0f,
    val climateData: ClimateData? = null,

    val stationInfo: StationInfo? = null,
    // Legacy fields for backward compatibility
    val stationName: String? = null,
    val isGreenStation: Boolean = false,
    
    val stationCode: String? = null,
    val isUpdatingStation: Boolean = false,
    val showStationCodeDialog: Boolean = false,
    val stationCodeError: String? = null,

    val showClimateStatusSheet: Boolean = false,
    val showBatterySheet: Boolean = false,
    val showCarbonBatterySheet: Boolean = false,
    val showStationSheet: Boolean = false,
    val showGridIntensitySheet: Boolean = false,
    val showElectricityConsumptionSheet: Boolean = false,
    val showAvoidedEmissionsSheet: Boolean = false,
    val showCarbonIntensityMetricSheet: Boolean = false,

    /** Persists selected bottom nav tab so back from profile sub-screens returns to Profile, not Home */
    val selectedBottomTabIndex: Int = 0,

    val error: String? = null
)

// 2. Intents (Actions triggered by UI)
sealed class ActivityIntent {
    object RefreshData : ActivityIntent()
    object Initialize : ActivityIntent()
    object Logout : ActivityIntent()
    object NavigateToLogin : ActivityIntent()
    data class SubmitStationCode(val stationCode: String) : ActivityIntent()
    object OpenStationCodeDialog : ActivityIntent()
    object CloseStationCodeDialog : ActivityIntent()
    object DismissStationCodeDialog : ActivityIntent()
    object OpenClimateStatusSheet : ActivityIntent()
    object DismissClimateStatusSheet : ActivityIntent()
    object OpenBatterySheet : ActivityIntent()
    object DismissBatterySheet : ActivityIntent()
    object OpenCarbonBatterySheet : ActivityIntent()
    object DismissCarbonBatterySheet : ActivityIntent()
    object OpenStationSheet : ActivityIntent()
    object DismissStationSheet : ActivityIntent()
    object OpenGridIntensitySheet : ActivityIntent()
    object DismissGridIntensitySheet : ActivityIntent()
    object OpenElectricityConsumptionSheet : ActivityIntent()
    object DismissElectricityConsumptionSheet : ActivityIntent()
    object OpenAvoidedEmissionsSheet : ActivityIntent()
    object DismissAvoidedEmissionsSheet : ActivityIntent()
    object OpenCarbonIntensityMetricSheet : ActivityIntent()
    object DismissCarbonIntensityMetricSheet : ActivityIntent()
    data class SelectBottomTab(val index: Int) : ActivityIntent()
}

// 3. Effects (One-off events like Navigation/Toast)
sealed class ActivityEffect {
    data class ShowToast(val message: String) : ActivityEffect()
    object NavigateToLogin : ActivityEffect()
    object StationUpdateSuccess : ActivityEffect()
    data class StationUpdateError(val message: String) : ActivityEffect()
}