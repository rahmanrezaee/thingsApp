package com.example.thingsappandroid.features.activity.viewModel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.model.DeviceInfoApiResponse
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.BatteryMonitor
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.util.ClimateUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import io.sentry.Breadcrumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// Helper data class for multiple return values
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class ActivityViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val repository = ThingsRepository()
    private val tokenManager = TokenManager(application)
    private val batteryMonitor = BatteryMonitor(application)
    private val preferenceManager = PreferenceManager(application)
    
    // Track previous charging state to detect plug-in events
    private var wasCharging = false

    // MVI State
    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    // MVI Effect
    private val _effect = Channel<ActivityEffect>()
    val effect = _effect.receiveAsFlow()

    // Cached Device ID
    private var cachedDeviceId: String? = null

    init {
        dispatch(ActivityIntent.Initialize)
    }

    fun dispatch(intent: ActivityIntent) {
        when (intent) {
            ActivityIntent.Initialize -> initialize()
            ActivityIntent.RefreshData -> refreshServerData()
            ActivityIntent.Logout -> performLogout()
            ActivityIntent.NavigateToLogin -> {
                viewModelScope.launch { _effect.send(ActivityEffect.NavigateToLogin) }
            }
            is ActivityIntent.SubmitStationCode -> submitStationCode(intent.stationCode)
            ActivityIntent.OpenStationCodeDialog -> {
                _state.update { it.copy(showStationCodeDialog = true) }
            }
            ActivityIntent.CloseStationCodeDialog,
            ActivityIntent.DismissStationCodeDialog -> {
                _state.update { it.copy(showStationCodeDialog = false, stationCodeError = null) }
            }
            ActivityIntent.OpenClimateStatusSheet -> {
                _state.update { it.copy(showClimateStatusSheet = true) }
            }
            ActivityIntent.DismissClimateStatusSheet -> {
                _state.update { it.copy(showClimateStatusSheet = false) }
            }
            ActivityIntent.OpenBatterySheet -> {
                _state.update { it.copy(showBatterySheet = true) }
            }
            ActivityIntent.DismissBatterySheet -> {
                _state.update { it.copy(showBatterySheet = false) }
            }
            ActivityIntent.OpenCarbonBatterySheet -> {
                _state.update { it.copy(showCarbonBatterySheet = true) }
            }
            ActivityIntent.DismissCarbonBatterySheet -> {
                _state.update { it.copy(showCarbonBatterySheet = false) }
            }
            ActivityIntent.OpenStationSheet -> {
                _state.update { it.copy(showStationSheet = true) }
            }
            ActivityIntent.DismissStationSheet -> {
                _state.update { it.copy(showStationSheet = false) }
            }
            ActivityIntent.OpenGridIntensitySheet -> {
                _state.update { it.copy(showGridIntensitySheet = true) }
            }
            ActivityIntent.DismissGridIntensitySheet -> {
                _state.update { it.copy(showGridIntensitySheet = false) }
            }
            ActivityIntent.OpenElectricityConsumptionSheet -> {
                _state.update { it.copy(showElectricityConsumptionSheet = true) }
            }
            ActivityIntent.DismissElectricityConsumptionSheet -> {
                _state.update { it.copy(showElectricityConsumptionSheet = false) }
            }
            ActivityIntent.OpenAvoidedEmissionsSheet -> {
                _state.update { it.copy(showAvoidedEmissionsSheet = true) }
            }
            ActivityIntent.DismissAvoidedEmissionsSheet -> {
                _state.update { it.copy(showAvoidedEmissionsSheet = false) }
            }
            ActivityIntent.OpenCarbonIntensityMetricSheet -> {
                _state.update { it.copy(showCarbonIntensityMetricSheet = true) }
            }
            ActivityIntent.DismissCarbonIntensityMetricSheet -> {
                _state.update { it.copy(showCarbonIntensityMetricSheet = false) }
            }
        }
    }

    private fun performLogout() {
        viewModelScope.launch {
            tokenManager.clearToken()
            _effect.send(ActivityEffect.NavigateToLogin)
        }
    }

    @SuppressLint("HardwareIds")
    private suspend fun getDeviceId(): String {
        if (cachedDeviceId == null) {
            cachedDeviceId = withContext(Dispatchers.IO) {
                Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID)
                    ?: UUID.randomUUID().toString()
            }
        }
        return cachedDeviceId!!
    }

    /**
     * Gets the current device location for carbon intensity calculations.
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permissions.
     * Falls back to default coordinates if location is unavailable.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double> {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                // Check if location permissions are granted
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasFineLocation && !hasCoarseLocation) {
                    Log.w("ActivityViewModel", "Location permissions not granted, using default coordinates")
                    return@withContext Pair(0.0, 0.0)
                }

                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                // Try to get last known location from GPS provider first, then network
                val providers = arrayOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER
                )

                for (provider in providers) {
                    if (locationManager.isProviderEnabled(provider)) {
                        try {
                            val location = locationManager.getLastKnownLocation(provider)
                            if (location != null) {
                                Log.d("ActivityViewModel", "Got location from $provider: ${location.latitude}, ${location.longitude}")
                                return@withContext Pair(location.latitude, location.longitude)
                            }
                        } catch (e: SecurityException) {
                            Log.w("ActivityViewModel", "Security exception getting location from $provider: ${e.message}")
                        } catch (e: Exception) {
                            Log.w("ActivityViewModel", "Error getting location from $provider: ${e.message}")
                        }
                    }
                }

                try {
                    Log.w("ActivityViewModel", "No location found from LocationManager, using default coordinates")
                } catch (e: Exception) {
                    Log.w("ActivityViewModel", "Error with FusedLocationProvider: ${e.message}")
                }

                // Default fallback coordinates (could be a default city like London or user's last known location)
                Log.w("ActivityViewModel", "Using default coordinates")
                Pair(51.5074, -0.1278) // London coordinates as fallback

            } catch (e: Exception) {
                Log.e("ActivityViewModel", "Error getting current location: ${e.message}")
                Pair(0.0, 0.0) // Ultimate fallback
            }
        }
    }

    private suspend fun loadDeviceInfo() {
        _state.update { it.copy(isLoading = true) }
        val deviceId = getDeviceId()
        
        // Run location and API calls on IO thread to avoid blocking main thread
        val (latitude, longitude, wifiAddress, stationCode) = withContext(Dispatchers.IO) {
            val loc = getCurrentLocation()
            val wifi = WifiUtils.getHashedWiFiBSSID(getApplication())
            val station = _state.value.stationCode
            Quadruple(loc.first, loc.second, wifi, station)
        }

        try {
            Log.d("ActivityViewModel", "Loading device info with coordinates: $latitude, $longitude")
            Log.d("ActivityViewModel", "WiFi Address: $wifiAddress")
            Log.d("ActivityViewModel", "Station Code: $stationCode")
            
            val response = withContext(Dispatchers.IO) {
                NetworkModule.api.getDeviceInfo(
                    DeviceInfoRequest(
                        deviceId = deviceId,
                        stationCode = stationCode,
                        wifiAddress = wifiAddress,
                        latitude = latitude,
                        longitude = longitude,
                        currentVersion = "1.0.0" // Could get from BuildConfig.VERSION_NAME
                    )
                )
            }

            if (response.isSuccessful && response.body() != null) {
                // API returns nested structure: {"Data":{...},"StatusCode":200,"Message":"success"}
                val apiResponse = response.body()!!
                val deviceInfo = apiResponse.data
                if (deviceInfo == null) {
                    Log.w("ActivityViewModel", "API response has no data field")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "API response has no data field"
                        )
                    }
                    return
                }
                Log.d("ActivityViewModel", "deviceInfo: ${deviceInfo},")

                // ClimateStatus is now Int in the response
                val mappedClimate = deviceInfo.climateStatus?.let { statusInt ->
                    ClimateUtils.getMappedClimateData(statusInt)
                } ?: ClimateUtils.getMappedClimateData(null as String?)

                _state.update {
                    it.copy(
                        isLoading = false,
                        deviceInfo = deviceInfo,
                        avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                        totalConsumedKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                        carbonIntensity = deviceInfo.carbonInfo?.currentIntensity?.toInt() ?: it.carbonIntensity,
                        stationInfo = deviceInfo.stationInfo, // Set the full StationInfo object
                        stationName = deviceInfo.stationInfo?.stationName,
                        isGreenStation = deviceInfo.stationInfo?.isGreen == true,
                        climateData = mappedClimate,
                        error = null // Clear any previous errors
                    )
                }
            } else {
                Log.d("ActivityViewModel", "Error: ${response.code()}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load device info: ${response.code()}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("ActivityViewModel", "Error: ${e.message}")
            viewModelScope.launch {
                val deviceIdForSentry = try { getDeviceId() } catch (ex: Exception) { "unknown" }
                Sentry.withScope { scope ->
                    scope.setTag("operation", "loadDeviceInfo")
                    scope.setContexts("device", mapOf("device_id" to deviceIdForSentry))
                    Sentry.captureException(e)
                }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "Error loading device info: ${e.message}"
                )
            }
        }
    }

    private fun initialize() {
        startServices()

        // Set Device Name
        _state.update { it.copy(deviceName = Build.MODEL) }
        
        // Get battery capacity in mWh - moved to background thread to avoid blocking main thread
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val batteryCapacityMwh = BatteryUtil.getBatteryCapacityMwh(getApplication())
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(batteryCapacityMwh = batteryCapacityMwh) }
                }
            } catch (e: Exception) {
                Log.w("ActivityViewModel", "Failed to get battery capacity: ${e.message}")
                Sentry.withScope { scope ->
                    scope.setTag("operation", "getBatteryCapacity")
                    scope.level = io.sentry.SentryLevel.WARNING
                    Sentry.captureException(e)
                }
                // Don't show Toast on background thread - it's not critical
            }
        }

        // Load device info first
        viewModelScope.launch {
            loadDeviceInfo()
        }

        // Combine local flows into State
        viewModelScope.launch {
            combine(
                batteryMonitor.batteryLevel,
                batteryMonitor.isCharging,
                batteryMonitor.consumption
            ) { args: Array<Any?> ->
                args
            }.collect { args ->
                val battery = args[0] as Float
                val charging = args[1] as Boolean
                val consumption = args[2] as Float

                // BatteryService handles station code notification, no need to show here
                // Just track charging state for other purposes
                if (charging) {
                    // Charging started
                } else {
                    // Charging stopped
                    if (_state.value.showStationCodeDialog) {
                        _state.update { it.copy(showStationCodeDialog = false, stationCodeError = null) }
                    }
                }
                
                wasCharging = charging

                _state.update {
                    it.copy(
                        batteryLevel = battery,
                        isCharging = charging,
                        currentUsageKwh = consumption
                    )
                }
            }
        }

        // Start Sync Loop
        viewModelScope.launch {
            // Authentication is guaranteed by Splash/Login at this point
            while(true) {
                dispatch(ActivityIntent.RefreshData)
                delay(60_000) // Sync every minute
            }
        }
    }

    private fun refreshServerData() {
        viewModelScope.launch {
            val id = getDeviceId()

            // Upload Consumption (Snapshot) - only if we have device info loaded
            val currentState = _state.value
            if (currentState.deviceInfo != null && currentState.currentUsageKwh > 0) {
                // Check API level for uploadConsumption as it requires API 26
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Get current location for consumption upload
                    val (latitude, longitude) = getCurrentLocation()
                    
                    repository.uploadConsumption(
                        context = getApplication(),
                        deviceId = id,
                        watts = currentState.currentUsageKwh * 1000 / 1.0,
                        kwh = currentState.currentUsageKwh.toDouble(),
                        batteryLevel = (currentState.batteryLevel * 100).toInt(),
                        isCharging = currentState.isCharging,
                        deviceInfo = currentState.deviceInfo,
                        latitude = latitude,
                        longitude = longitude,
                        stationCode = currentState.stationCode
                    )
                }
            }
        }
    }

    private fun startServices() {
        batteryMonitor.startMonitoring()
    }

    override fun onCleared() {
        super.onCleared()
        batteryMonitor.stopMonitoring()
    }
    
    private fun submitStationCode(stationCode: String) {
        viewModelScope.launch {
            val stationBreadcrumb = Breadcrumb("User submitting station code")
            stationBreadcrumb.category = "user"
            stationBreadcrumb.level = io.sentry.SentryLevel.INFO
            stationBreadcrumb.setData("station_code", stationCode)
            Sentry.addBreadcrumb(stationBreadcrumb)
            _state.update {
                it.copy(
                    isUpdatingStation = true,
                    stationCode = stationCode,
                    stationCodeError = null
                )
            }
            try {
                val deviceId = getDeviceId()
                val result = repository.setStation(deviceId, stationCode)

                if (result.first) {
                    _effect.send(ActivityEffect.StationUpdateSuccess)
                    _state.update { it.copy(showStationCodeDialog = false) }
                    // Refresh device info to get updated station info
                    loadDeviceInfo()
                } else {
                    // Keep dialog open and show error
                    val errorMsg = result.second ?: "Failed to update station code"
                    _state.update { it.copy(stationCodeError = errorMsg) }
                }
            } catch (e: Exception) {
                Log.e("ActivityViewModel", "Error submitting station code: ${e.message}")
                val deviceId = try { getDeviceId() } catch (ex: Exception) { "unknown" }
                Sentry.withScope { scope ->
                    scope.setTag("operation", "submitStationCode")
                    scope.setContexts("station", mapOf(
                        "device_id" to deviceId,
                        "station_code" to stationCode
                    ))
                    Sentry.captureException(e)
                }
                _state.update { it.copy(stationCodeError = "Error: ${e.message}") }
            } finally {
                _state.update { it.copy(isUpdatingStation = false) }
            }
        }
    }
}