package com.example.thingsappandroid.features.home.viewModel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.services.BatteryServiceActions
import com.example.thingsappandroid.util.ClimateUtils
import com.example.thingsappandroid.util.WifiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import io.sentry.Breadcrumb
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val tokenManager: TokenManager,
    private val preferenceManager: PreferenceManager,
    private val repository: ThingsRepository
) : AndroidViewModel(application) {

    // MVI State
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // MVI Effect
    private val _effect = Channel<ActivityEffect>()
    val effect = _effect.receiveAsFlow()

    // Cached Device ID
    private var cachedDeviceId: String? = null

    // Last known WiFi BSSID - when it changes, we refresh device info
    private var lastKnownWifiBssid: String? = null

    // Periodic battery update job
    private var batteryUpdateJob: kotlinx.coroutines.Job? = null

    private var wifiBroadcastReceiver: BroadcastReceiver? = null
    private var locationBroadcastReceiver: BroadcastReceiver? = null

    init {
        lastKnownWifiBssid = preferenceManager.getLastWifiBssid()
        registerWifiChangeReceiver()
        registerLocationReceiver()
        dispatch(ActivityIntent.Initialize)
    }

    fun dispatch(intent: ActivityIntent) {
        when (intent) {
            ActivityIntent.Initialize -> initialize()
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
            is ActivityIntent.SelectBottomTab -> {
                _state.update { it.copy(selectedBottomTabIndex = intent.index) }
            }
            ActivityIntent.RefreshData -> {
                // Check location before refreshing data
                checkLocationAndLoadDeviceInfo()
            }
            ActivityIntent.CheckLocationStatus -> {
                checkLocationAndLoadDeviceInfo()
            }
            ActivityIntent.LocationEnabled -> {
                // User enabled location, now proceed with device info loading
                _state.update { 
                    it.copy(
                        isLocationEnabled = true,
                        showLocationEnableDialog = false,
                        locationRequestSkipped = false
                    ) 
                }
                if (_state.value.pendingDeviceInfoLoad) {
                    viewModelScope.launch {
                        loadDeviceInfoWithRetry()
                    }
                }
            }
            ActivityIntent.SkipLocationRequest -> {
                _state.update {
                    it.copy(
                        showLocationEnableDialog = false,
                        locationRequestSkipped = true,
                        pendingDeviceInfoLoad = false
                    )
                }
                viewModelScope.launch {
                    loadCachedOrDefaultDeviceInfo()
                }
            }
            ActivityIntent.ShowWifiError, ActivityIntent.DismissWifiError,
            ActivityIntent.OpenLocationSettings, ActivityIntent.OpenWifiSettings -> {
                // Handled by UI layer - these are navigation/actions that don't need VM processing
            }
        }
    }
    
    /**
     * Checks WiFi and location.
     * - If WiFi on and location on: load device info from API, send broadcast for BatteryService to update notification.
     * - If WiFi on and location off: show "enable location" dialog and load cache/default (do not set locationRequestSkipped).
     * - If WiFi off: use local DB or create default, no dialog.
     */
    private fun checkLocationAndLoadDeviceInfo() {
        val locationEnabled = isLocationEnabled()
        val wifiConnected = isWiFiConnected()
        val skipped = _state.value.locationRequestSkipped

        if (wifiConnected && locationEnabled) {
            _state.update {
                it.copy(
                    isLocationEnabled = true,
                    showLocationEnableDialog = false,
                    pendingDeviceInfoLoad = false
                )
            }
            viewModelScope.launch {
                loadDeviceInfoWithRetry()
                // loadDeviceInfoWithRetry saves device info and sends DEVICEINFO_UPDATED for BatteryService.updateNotification()
            }
        } else if (wifiConnected && !locationEnabled) {
            // WiFi on but location off: show dialog and display cached/default data
            if (!skipped) {
                _state.update { it.copy(showLocationEnableDialog = true) }
            }
            viewModelScope.launch {
                _state.update { it.copy(isLoading = false) } // Stop refresh indicator immediately (no API call)
                loadCachedOrDefaultDeviceInfo(setLocationSkipped = false)
            }
        } else {
            // No WiFi: use local DB or create default, no dialog
            Log.d("ActivityViewModel", "No WiFi - loading from local DB or creating default")
            viewModelScope.launch {
                _state.update { it.copy(isLoading = false) } // Stop refresh indicator immediately (no API call)
                loadCachedOrDefaultDeviceInfo(setLocationSkipped = true)
            }
        }
    }

    private fun isWiFiConnected(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            ni?.type == ConnectivityManager.TYPE_WIFI && ni.isConnected
        }
    }

    /**
     * Load device info from local DB; if none, create default (ClimateStatus 8, carbon 100% 500g, grid 485 gCO₂e) and save.
     * @param setLocationSkipped when true (e.g. user skipped or no WiFi), set locationRequestSkipped so we don't show enable-location dialog again.
     */
    private suspend fun loadCachedOrDefaultDeviceInfo(setLocationSkipped: Boolean = true) {
        val deviceId = getDeviceId()
        val cachedDeviceInfo = preferenceManager.getLastDeviceInfo()
        val deviceInfo = if (cachedDeviceInfo != null) {
            Log.d("ActivityViewModel", "Loading cached device info")
            cachedDeviceInfo
        } else {
            Log.d("ActivityViewModel", "No cached data - creating default device info")
            val defaultInfo = repository.createDefaultDeviceInfo(deviceId)
            preferenceManager.saveLastDeviceInfo(defaultInfo)
            defaultInfo
        }
        val mappedClimate = deviceInfo.climateStatus?.let { statusInt ->
            ClimateUtils.getMappedClimateData(statusInt)
        } ?: ClimateUtils.getMappedClimateData(null as String?)
        _state.update {
            it.copy(
                deviceInfo = deviceInfo,
                avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                totalConsumedKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                carbonIntensity = deviceInfo.carbonInfo?.currentIntensity?.toInt() ?: it.carbonIntensity,
                stationInfo = deviceInfo.stationInfo,
                stationName = deviceInfo.stationInfo?.stationName,
                isGreenStation = deviceInfo.stationInfo?.isGreen == true,
                climateData = mappedClimate,
                isLoading = false,
                error = null,
                locationRequestSkipped = setLocationSkipped,
                deviceName = deviceInfo.alias ?: Build.MODEL
            )
        }
    }

    private fun performLogout() {
        viewModelScope.launch {
            tokenManager.clearToken()
            _effect.send(ActivityEffect.NavigateToLogin)
        }
    }

    /**
     * Checks if location services are enabled (GPS or Network provider)
     */
    private fun isLocationEnabled(): Boolean {
        val context = getApplication<Application>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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

                // Default fallback coordinates (could be a default city like London or user's last known location)
                Log.w("ActivityViewModel", "No location found from LocationManager, using default coordinates")
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

        val (wifiAddress, stationCode) = withContext(Dispatchers.IO) {
            val wifi = WifiUtils.getHashedWiFiBSSID(getApplication())
            val station = _state.value.stationCode
            Pair(wifi, station)
        }

        if (wifiAddress.isNullOrBlank()) {
            Log.d("ActivityViewModel", "WiFi address missing - not sending getDeviceInfo request")
            loadCachedOrDefaultDeviceInfo()
            return
        }

        try {
            Log.d("ActivityViewModel", "WiFi Address: $wifiAddress, Station Code: $stationCode")
            val response = withContext(Dispatchers.IO) {
                NetworkModule.api.getDeviceInfo(
                    DeviceInfoRequest(
                        deviceId = deviceId,
                        stationCode = stationCode,
                        wifiAddress = wifiAddress,
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

                val hasStation = deviceInfo.stationInfo != null
                preferenceManager.setHasStation(hasStation)
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
                // Save to cache for next time, then notify BatteryService to update notification
                preferenceManager.saveLastDeviceInfo(deviceInfo)
                lastKnownWifiBssid = wifiAddress
                preferenceManager.saveLastWifiBssid(wifiAddress)
                getApplication<Application>().sendBroadcast(Intent(BatteryServiceActions.DEVICEINFO_UPDATED))
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

    /**
     * Loads device info from API once. On failure, falls back to cache or default.
     */
    private suspend fun loadDeviceInfoWithRetry() {
        _state.update { it.copy(isLoading = true, error = null) }

        val deviceId = getDeviceId()
        val (wifiAddress, stationCode) = withContext(Dispatchers.IO) {
            val wifi = WifiUtils.getHashedWiFiBSSID(getApplication())
            val station = _state.value.stationCode
            Pair(wifi, station)
        }

        if (wifiAddress.isNullOrBlank()) {
            Log.d("ActivityViewModel", "WiFi address missing - not sending getDeviceInfo request")
            loadCachedOrDefaultDeviceInfo()
            return
        }

        try {
            val response = withContext(Dispatchers.IO) {
                NetworkModule.api.getDeviceInfo(
                    DeviceInfoRequest(
                        deviceId = deviceId,
                        stationCode = stationCode,
                        wifiAddress = wifiAddress,
                        currentVersion = "1.0.0"
                    )
                )
            }

            if (response.isSuccessful && response.body() != null) {
                val deviceInfo = response.body()!!.data
                if (deviceInfo == null) {
                    Log.d("ActivityViewModel", "API response has no data field")
                    loadCachedOrDefaultDeviceInfo()
                    return
                }

                val mappedClimate = deviceInfo.climateStatus?.let { statusInt ->
                    ClimateUtils.getMappedClimateData(statusInt)
                } ?: ClimateUtils.getMappedClimateData(null as String?)

                val hasStation = deviceInfo.stationInfo != null
                preferenceManager.setHasStation(hasStation)
                _state.update {
                    it.copy(
                        isLoading = false,
                        deviceInfo = deviceInfo,
                        avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                        totalConsumedKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                        carbonIntensity = deviceInfo.carbonInfo?.currentIntensity?.toInt() ?: it.carbonIntensity,
                        stationInfo = deviceInfo.stationInfo,
                        stationName = deviceInfo.stationInfo?.stationName,
                        isGreenStation = deviceInfo.stationInfo?.isGreen == true,
                        climateData = mappedClimate,
                        error = null
                    )
                }
                preferenceManager.saveLastDeviceInfo(deviceInfo)
                lastKnownWifiBssid = wifiAddress
                preferenceManager.saveLastWifiBssid(wifiAddress)
                getApplication<Application>().sendBroadcast(Intent(BatteryServiceActions.DEVICEINFO_UPDATED))
                Log.d("ActivityViewModel", "Device info loaded and saved to cache")
            } else {
                Log.d("ActivityViewModel", "getDeviceInfo failed: ${response.code()}")
                loadCachedOrDefaultDeviceInfo()
            }
        } catch (e: Exception) {
            Log.d("ActivityViewModel", "getDeviceInfo error: ${e.message}")
            viewModelScope.launch {
                val deviceIdForSentry = try { getDeviceId() } catch (ex: Exception) { "unknown" }
                Sentry.withScope { scope ->
                    scope.setTag("operation", "loadDeviceInfo")
                    scope.setContexts("device", mapOf("device_id" to deviceIdForSentry))
                    Sentry.captureException(e)
                }
            }
            loadCachedOrDefaultDeviceInfo()
        }
    }

    /**
     * Reads current battery status from system using BatteryManager API
     */
    private fun readBatteryStatus() {
        try {
            val context = getApplication<Application>()
            
            // Method 1: Use BatteryManager for reliable percentage (0-100)
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            // Method 2: Also try sticky broadcast as backup
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val calculatedPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            
            // Get charging status from sticky broadcast
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            

                _state.update {
                    it.copy(
                        batteryLevel = calculatedPct.toFloat() / 100,
                        isCharging = isCharging,
                        hasBatteryData = true
                    )
                }

        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error reading battery status", e)
        }
    }

    /**
     * Starts periodic battery status updates every 1 second
     */
    private fun startPeriodicBatteryUpdates() {
        batteryUpdateJob?.cancel()
        
        // Read battery immediately first
        readBatteryStatus()
        
        batteryUpdateJob = viewModelScope.launch {
            while (this.isActive) {
                delay(1000) // Wait 1 second before next read
                readBatteryStatus()
            }
        }
        Log.d("HomeViewModel", "Periodic battery updates started (1 second interval)")
    }

    private fun initialize() {
        // Start periodic battery updates every 1 second
        startPeriodicBatteryUpdates()

        // Set Device Name and sync location-skipped from preferences (set on SplashScreen)
        _state.update {
            it.copy(
                deviceName = Build.MODEL,
                locationRequestSkipped = preferenceManager.getLocationRequestSkipped()
            )
        }
        
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
                    scope.level = SentryLevel.WARNING
                    Sentry.captureException(e)
                }
                // Don't show Toast on background thread - it's not critical
            }
        }

        // Load cached device info first for immediate display (doesn't require location)
        viewModelScope.launch {
            val cachedDeviceInfo = preferenceManager.getLastDeviceInfo()
            if (cachedDeviceInfo != null) {
                Log.d("ActivityViewModel", "Loading cached device info for immediate display")
                val mappedClimate = cachedDeviceInfo.climateStatus?.let { statusInt ->
                    ClimateUtils.getMappedClimateData(statusInt)
                } ?: ClimateUtils.getMappedClimateData(null as String?)
                
                _state.update {
                    it.copy(
                        deviceInfo = cachedDeviceInfo,
                        avoidedEmissions = cachedDeviceInfo.totalAvoided?.toFloat() ?: 0f,
                        totalConsumedKwh = cachedDeviceInfo.totalConsumed?.toFloat() ?: 0f,
                        carbonIntensity = cachedDeviceInfo.carbonInfo?.currentIntensity?.toInt() ?: it.carbonIntensity,
                        stationInfo = cachedDeviceInfo.stationInfo,
                        stationName = cachedDeviceInfo.stationInfo?.stationName,
                        isGreenStation = cachedDeviceInfo.stationInfo?.isGreen == true,
                        climateData = mappedClimate,
                        isLoading = false,
                        error = null,
                        deviceName = cachedDeviceInfo.alias ?: Build.MODEL
                    )
                }
            }
            
            // Then check WiFi/location and refresh from API or use cache/default
            checkLocationAndLoadDeviceInfo()
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

    
    private fun submitStationCode(stationCode: String) {
        viewModelScope.launch {
            val stationBreadcrumb = Breadcrumb("User submitting station code")
            stationBreadcrumb.category = "user"
            stationBreadcrumb.level = SentryLevel.INFO
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
                val request = com.example.thingsappandroid.data.model.SetStationRequest(
                    deviceId = deviceId,
                    stationCode = stationCode
                )
                val result = NetworkModule.api.setStation(request)

                if (result.isSuccessful) {
                    _effect.send(ActivityEffect.StationUpdateSuccess)
                    _state.update { it.copy(showStationCodeDialog = false) }
                    // Refresh device info to get updated station info
                    loadDeviceInfo()
                } else {
                    // Keep dialog open and show error
                    val errorMsg = result.errorBody()?.string() ?: "Failed to update station code"
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

    /**
     * Registers a BroadcastReceiver to listen for WiFi/network changes.
     * When BSSID changes, triggers a device info refresh.
     */
    private fun registerWifiChangeReceiver() {
        if (wifiBroadcastReceiver != null) return
        wifiBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModelScope.launch {
                    checkWifiAndRefreshIfNeeded()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(app, wifiBroadcastReceiver!!, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(wifiBroadcastReceiver, filter)
        }
        Log.d("HomeViewModel", "WiFi change receiver registered")
    }

    private fun unregisterWifiChangeReceiver() {
        wifiBroadcastReceiver?.let { receiver ->
            try {
                getApplication<Application>().unregisterReceiver(receiver)
                Log.d("HomeViewModel", "WiFi change receiver unregistered")
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Error unregistering WiFi receiver: ${e.message}")
            }
            wifiBroadcastReceiver = null
        }
    }

    /**
     * Registers a BroadcastReceiver to listen for location provider changes.
     * When location is enabled (e.g. user returns from Settings), triggers checkLocationAndLoadDeviceInfo().
     */
    private fun registerLocationReceiver() {
        if (locationBroadcastReceiver != null) return
        locationBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("HomeViewModel", "Location provider changed: ${intent?.action}")
                checkLocationAndLoadDeviceInfo()
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(app, locationBroadcastReceiver!!, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(locationBroadcastReceiver, filter)
        }
        Log.d("HomeViewModel", "Location change receiver registered")
    }

    private fun unregisterLocationReceiver() {
        locationBroadcastReceiver?.let { receiver ->
            try {
                getApplication<Application>().unregisterReceiver(receiver)
                Log.d("HomeViewModel", "Location change receiver unregistered")
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Error unregistering location receiver: ${e.message}")
            }
            locationBroadcastReceiver = null
        }
    }

    /**
     * Checks if WiFi BSSID has changed. If so, triggers device info refresh.
     * Called on WiFi broadcast and on app resume.
     */
    private suspend fun checkWifiAndRefreshIfNeeded() {
        val currentBssid = withContext(Dispatchers.IO) {
            WifiUtils.getHashedWiFiBSSID(getApplication())
        }
        if (currentBssid != lastKnownWifiBssid) {
            Log.d("HomeViewModel", "WiFi BSSID changed: $lastKnownWifiBssid -> $currentBssid, refreshing device info")
            checkLocationAndLoadDeviceInfo()
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterWifiChangeReceiver()
        unregisterLocationReceiver()
        batteryUpdateJob?.cancel()
        batteryUpdateJob = null
        Log.d("HomeViewModel", "Periodic battery updates cancelled")
    }
}