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
import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.utils.ClimateStatusManager
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
import kotlinx.coroutines.yield
import retrofit2.Response
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

    /** Friendly device name: e.g. "Samsung SM-S908B", "Xiaomi 12T", "Google Pixel 7 Pro" */
    private val friendlyDeviceName: String by lazy {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    // Last known WiFi BSSID - when it changes, we refresh device info
    private var lastKnownWifiBssid: String? = null

    // Periodic battery update job
    private var batteryUpdateJob: kotlinx.coroutines.Job? = null

    /** Track if initialization has been completed to prevent duplicate calls */
    private var isInitialized = false

    /** Reference count for overlapping getDeviceInfo/SetClimateStatus API calls. Loading stays visible until all finish. */
    private var loadingRefCount = 0

    /**
     * Single internal broadcast receiver that handles all broadcast intents
     */
    private val internalBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: "null"
            Log.d(HOME_LOG, "📡 Broadcast received: $action")
            when (action) {
                BatteryServiceActions.DEVICEINFO_UPDATED -> handleDeviceInfoUpdated()
                BatteryServiceActions.LOADING_STARTED -> {
                    loadingRefCount++
                    _state.update { it.copy(isLoading = true) }
                }
                BatteryServiceActions.LOADING_FINISHED -> {
                    loadingRefCount = maxOf(0, loadingRefCount - 1)
                    _state.update { it.copy(isLoading = loadingRefCount > 0) }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION,
                LocationManager.MODE_CHANGED_ACTION -> handleLocationChanged(action)
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> handleWifiConnectivityChanged()
                else -> Log.d(HOME_LOG, "⚠️ Unknown broadcast action: $action")
            }
        }
    }

    private companion object {
        private const val HOME_LOG = "HomeViewModel"
        
        /**
         * Helper function to register a broadcast receiver with proper SDK version handling
         */
        private fun Context.registerReceiverCompat(
            receiver: BroadcastReceiver,
            filter: IntentFilter
        ) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    ContextCompat.registerReceiver(
                        this,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                }
                else -> {
                    registerReceiver(receiver, filter)
                }
            }
        }
    }

    init {
        lastKnownWifiBssid = preferenceManager.getLastWifiBssid()
        registerBroadcastReceivers()
        dispatch(ActivityIntent.Initialize)
    }

    fun dispatch(intent: ActivityIntent) {
        when (intent) {
            ActivityIntent.Initialize -> {
                // Only initialize once
                if (!isInitialized) {
                    initialize()
                    isInitialized = true
                } else {
                    Log.d(HOME_LOG, "⏭️ Skipping initialization - already initialized")
                }
            }
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
                // User manually requested refresh (pull-to-refresh or retry) – ask BatteryService to run getDeviceInfo
                Log.d(HOME_LOG, "📥 Manual refresh requested")
                _state.update { it.copy(isLoading = true, error = null) }
                sendRequestGetDeviceInfo()
            }
            ActivityIntent.CheckLocationStatus -> {
                checkLocationAndLoadDeviceInfo()
            }
            ActivityIntent.LocationEnabled -> {
                // User enabled location (returned from Settings). BatteryService will run getDeviceInfo on its connectivity receiver; just load from cache.
                Log.d(HOME_LOG, "📍 Location enabled - loading from cache")
                _state.update { 
                    it.copy(
                        isLocationEnabled = true,
                        showLocationEnableDialog = false,
                        locationRequestSkipped = false,
                        pendingDeviceInfoLoad = false
                    ) 
                }
                viewModelScope.launch { loadCachedOrDefaultDeviceInfo(setLocationSkipped = false) }
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
        _state.update { it.copy(isLoading = true, error = null) }
        if (wifiConnected && locationEnabled) {
            // Service already runs getDeviceInfo on start and on WiFi/location changes; just load from cache.
            viewModelScope.launch {
                val wifiAddress = withContext(Dispatchers.IO) { WifiUtils.getHashedWiFiBSSID(getApplication()) }
                lastKnownWifiBssid = wifiAddress
                wifiAddress?.let { preferenceManager.saveLastWifiBssid(it) }
                _state.update {
                    it.copy(
                        isLocationEnabled = true,
                        showLocationEnableDialog = false,
                        pendingDeviceInfoLoad = false,
                        wifiAddress = wifiAddress
                    )
                }
                loadCachedOrDefaultDeviceInfo(setLocationSkipped = false)
            }
        } else if (wifiConnected) {
            if (!skipped) {
                _state.update { it.copy(showLocationEnableDialog = true) }
            }
            viewModelScope.launch {
                yield()
                loadCachedOrDefaultDeviceInfo(setLocationSkipped = false)
            }
        } else {
            Log.d(HOME_LOG, "No WiFi - loading from local DB or creating default")
            _state.update { it.copy(wifiAddress = null) }
            viewModelScope.launch {
                yield()
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
                deviceName = friendlyDeviceName,
                publicName = deviceInfo.alias ?: ""
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


    private fun sendRequestGetDeviceInfo() {
        Intent(BatteryServiceActions.REQUEST_GET_DEVICE_INFO).apply {
            setPackage(getApplication<Application>().packageName)
        }.also { getApplication<Application>().sendBroadcast(it) }
    }

    /**
     * Refreshes the auth token using the device ID.
     * @return The new token if successful, null otherwise.
     */
    private suspend fun refreshToken(): String? {
        return try {
            val deviceId = getDeviceId()
            Log.d(HOME_LOG, "refreshToken: attempting for deviceId=$deviceId")
            repository.authenticate(deviceId)?.also { newToken ->
                tokenManager.saveToken(newToken)
                NetworkModule.setAuthToken(newToken)
                Log.d(HOME_LOG, "refreshToken: success")
            }
        } catch (e: Exception) {
            Log.e(HOME_LOG, "refreshToken: error - ${e.message}")
            null
        }
    }

    /**
     * Handles authentication failure - clears token and navigates to login.
     */
    private fun handleAuthFailure() {
        Log.w(HOME_LOG, "handleAuthFailure: clearing token, navigating to login")
        tokenManager.clearToken()
        NetworkModule.setAuthToken(null)
        _state.update { it.copy(isLoading = false) }
        viewModelScope.launch { _effect.send(ActivityEffect.NavigateToLogin) }
    }

    /**
     * Reports exception to Sentry with device context.
     */
    private fun reportErrorToSentry(e: Exception) {
        viewModelScope.launch {
            val deviceId = runCatching { getDeviceId() }.getOrDefault("unknown")
            Sentry.withScope { scope ->
                scope.setTag("operation", "loadDeviceInfo")
                scope.setContexts("device", mapOf("device_id" to deviceId))
                Sentry.captureException(e)
            }
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
            
            // Get charging status from sticky broadcast using when expression
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                else -> false
            }
            
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

        // Set Device Name, WiFi status, wifiAddress (for About Green-Fi ID), and sync location-skipped from preferences
        _state.update {
            it.copy(
                deviceName = friendlyDeviceName,
                isWifiConnected = isWiFiConnected(),
                wifiAddress = preferenceManager.getLastWifiBssid(),
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
                        deviceName = friendlyDeviceName,
                        publicName = cachedDeviceInfo.alias ?: ""
                    )
                }
            }
            
            // Then check WiFi/location and load from cache (service runs getDeviceInfo on start and on WiFi/location changes)
            checkLocationAndLoadDeviceInfo()
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
                    if (_state.value.isCharging) {
                        Intent(BatteryServiceActions.FOR_NEW_DEVICE_CALL_CLIMATE_STATUS).apply {
                            setPackage(getApplication<Application>().packageName)
                            putExtra(BatteryServiceActions.EXTRA_STATION_CODE, stationCode)
                        }.also { getApplication<Application>().sendBroadcast(it) }
                    }
                } else {
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
     * Registers the internal broadcast receiver for all HomeViewModel broadcasts
     */
    @Suppress("DEPRECATION")
    private fun registerBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction(BatteryServiceActions.DEVICEINFO_UPDATED)
            addAction(BatteryServiceActions.LOADING_STARTED)
            addAction(BatteryServiceActions.LOADING_FINISHED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        getApplication<Application>().registerReceiverCompat(internalBroadcastReceiver, filter)
        Log.d(HOME_LOG, "✅ Internal broadcast receiver registered for WiFi, location and device info updates")
    }

    /**
     * Unregisters the internal broadcast receiver
     */
    private fun unregisterBroadcastReceivers() {
        runCatching {
            getApplication<Application>().unregisterReceiver(internalBroadcastReceiver)
            Log.d(HOME_LOG, "Internal broadcast receiver unregistered")
        }.onFailure { e ->
            Log.w(HOME_LOG, "Error unregistering internal broadcast receiver: ${e.message}")
        }
    }

    /**
     * Handles DEVICEINFO_UPDATED broadcast from BatteryService/ClimateStatusManager
     */
    private fun handleDeviceInfoUpdated() {
        Log.d(HOME_LOG, "✅ Received DEVICEINFO_UPDATED broadcast - refreshing UI from cache")
        viewModelScope.launch {
            lastKnownWifiBssid = preferenceManager.getLastWifiBssid()
            loadCachedOrDefaultDeviceInfo()
        }
    }

    /**
     * Handles location provider changes
     */
    private fun handleLocationChanged(action: String) {
        Log.d(HOME_LOG, "Location provider changed: $action")
        checkLocationAndLoadDeviceInfo()
    }

    /**
     * Handles WiFi connectivity changes. Updates isWifiConnected and wifiAddress (Green-Fi ID)
     * so About and other UI stay in sync when user toggles WiFi.
     */
    private fun handleWifiConnectivityChanged() {
        val isConnected = isWiFiConnected()
        Log.d(HOME_LOG, "WiFi connectivity changed: isConnected=$isConnected")
        if (!isConnected) {
            _state.update { it.copy(isWifiConnected = false, wifiAddress = null) }
            return
        }
        _state.update { it.copy(isWifiConnected = true) }
        viewModelScope.launch {
            val bssid = withContext(Dispatchers.IO) {
                WifiUtils.getHashedWiFiBSSID(getApplication())
            }
            _state.update { it.copy(wifiAddress = bssid) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterBroadcastReceivers()
        batteryUpdateJob?.cancel()
        batteryUpdateJob = null
        Log.d(HOME_LOG, "HomeViewModel cleared - cancelled all jobs")
    }
}
