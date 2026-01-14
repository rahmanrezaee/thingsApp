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
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.BatteryMonitor
import com.example.thingsappandroid.util.ClimateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ActivityViewModel(application: Application) : AndroidViewModel(application) {

    // Dependencies
    private val repository = ThingsRepository()
    private val tokenManager = TokenManager(application)
    private val batteryMonitor = BatteryMonitor(application)

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
        val (latitude, longitude) = getCurrentLocation()

        try {
            Log.d("ActivityViewModel", "Loading device info with coordinates: $latitude, $longitude")
            val response = NetworkModule.api.getDeviceInfo(
                DeviceInfoRequest(
                    deviceId = deviceId,
                    latitude = latitude,
                    longitude = longitude,
                    currentVersion = "1.0.0" // Could get from BuildConfig.VERSION_NAME
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val deviceInfo = response.body()!!
                Log.d("ActivityViewModel", "deviceInfo: ${deviceInfo},")

                val mappedClimate = deviceInfo.stationInfo?.climateStatus?.let { climateStatusStr ->
                    // Try to parse as Int first, then fallback to String
                    try {
                        val statusInt = climateStatusStr.toIntOrNull()
                        if (statusInt != null) {
                            ClimateUtils.getMappedClimateData(statusInt)
                        } else {
                            ClimateUtils.getMappedClimateData(climateStatusStr)
                        }
                    } catch (e: Exception) {
                        ClimateUtils.getMappedClimateData(climateStatusStr)
                    }
                } ?: ClimateUtils.getMappedClimateData(null as String?)

                _state.update {
                    it.copy(
                        isLoading = false,
                        deviceInfo = deviceInfo,
                        avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                        totalConsumedKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                        carbonIntensity = deviceInfo.carbonInfo?.currentIntensity?.toInt() ?: it.carbonIntensity,
                        stationName = deviceInfo.stationInfo?.stationName,
                        isGreenStation = deviceInfo.stationInfo?.isGreen == true,
                        climateData = mappedClimate
                    )
                }
            } else {

                Log.d("ActivityViewModel", "Error: ${response.code()},")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load device info: ${response.code()}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("ActivityViewModel", "Error: ${e.message},")
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
                    repository.uploadConsumption(
                        deviceId = id,
                        watts = currentState.currentUsageKwh * 1000 / 1.0,
                        kwh = currentState.currentUsageKwh.toDouble(),
                        batteryLevel = (currentState.batteryLevel * 100).toInt(),
                        isCharging = currentState.isCharging
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
}