package com.example.thingsappandroid.services.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.model.SetClimateStatusRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.services.BatteryServiceActions
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry

/**
 * Manages climate status API calls when charging starts.
 * Caller (BatteryService) must ensure WiFi and location are available before calling.
 */
class ClimateStatusManager(private val context: Context) {

    private val TAG = "ClimateStatusManager"

    /**
     * Calls SetClimateStatus then GetDeviceInfo. Updates preferences with result.
     * Caller should only invoke when wifi+location are ready.
     *
     * @return Climate status from API if successful, null otherwise
     */
    suspend fun handleChargingStarted(
        onLocationError: ((reason: String, details: String, isPermissionDenied: Boolean, isServicesDisabled: Boolean) -> Unit)? = null
    ): Int? {
        return try {
            val deviceId = DeviceUtils.getStoredDeviceId(context)
            if (deviceId == null) {
                Log.w(TAG, "handleChargingStarted skipped: no deviceId")
                return null
            }
            if (NetworkModule.getAuthToken().isNullOrBlank()) {
                Log.d(TAG, "handleChargingStarted skipped: no auth token")
                return null
            }

            val wifiResult = WifiUtils.getHashedWiFiBSSIDWithRetry(context, maxRetries = 3, delayMs = 1500L)
            if (!wifiResult.success || wifiResult.bssid == null) {
                Log.w(TAG, "WiFi BSSID not available: ${wifiResult.errorReason}")
                return null
            }

            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val hasLocationPermission = LocationUtils.hasLocationPermission(context)
            val isLocationEnabled = LocationUtils.isLocationEnabled(context)
            if (!hasLocationPermission || !isLocationEnabled) {
                onLocationError?.invoke(
                    if (!hasLocationPermission) "Location permission denied" else "Location services disabled",
                    "Location required for carbon tracking.",
                    !hasLocationPermission,
                    !isLocationEnabled
                )
                return null
            }

            setClimateStatusOnChargingStart(deviceId, wifiResult.bssid)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling charging event: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "handleChargingEvent")
                scope.setContexts("charging", mapOf(
                    "device_id" to (DeviceUtils.getStoredDeviceId(context) ?: "null")
                ))
                Sentry.captureException(e)
            }
            null
        }
    }
    
    /**
     * Call SetClimateStatus API when charging starts
     */
    private suspend fun setClimateStatusOnChargingStart(
        deviceId: String?,
        wiFiAddress: String?
    ): Int? {
        if (deviceId == null || wiFiAddress.isNullOrBlank()) {
            Log.d(TAG, "setClimateStatus skipped: missing deviceId or wiFiAddress")
            return null
        }
        
        if (NetworkModule.getAuthToken().isNullOrBlank()) {
            Log.d(TAG, "setClimateStatus skipped: no auth token")
            return null
        }
        
        var climateStatusInt: Int? = null
        try {
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val request = SetClimateStatusRequest(
                deviceId = deviceId,
                latitude = latitude,
                longitude = longitude,
                wiFiAddress = wiFiAddress
            )
            
            val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
                NetworkModule.api.setClimateStatus(request)
            }
            
            if (response?.isSuccessful == true) {
                response.body()?.data?.let { data ->
                    climateStatusInt = data.climateStatus
                    Log.d(TAG, "setClimateStatus success: isGreen=${data.isGreen}, climateStatus=${data.climateStatus}")
                }
            } else {
                Log.w(TAG, "setClimateStatus failed: ${response?.code() ?: "timeout"}")
            }

            // Call GetDeviceInfo
            getDeviceInfoOnChargingStart(deviceId, wiFiAddress)
            
        } catch (e: Exception) {
            Log.e(TAG, "setClimateStatus error: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "setClimateStatus")
                Sentry.captureException(e)
            }
        }
        return climateStatusInt
    }
    
    /**
     * Call GetDeviceInfo API when charging starts
     */
    private suspend fun getDeviceInfoOnChargingStart(
        deviceId: String,
        wiFiAddress: String
    ) {
        try {
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val request = DeviceInfoRequest(
                deviceId = deviceId,
                wifiAddress = wiFiAddress,
                latitude = latitude,
                longitude = longitude
            )
            
            val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
                NetworkModule.api.getDeviceInfo(request)
            }
            
            if (response?.isSuccessful == true) {
                response.body()?.data?.let { deviceInfo ->
                    Log.d(TAG, "getDeviceInfo success")
                    
                    // Save to PreferenceManager (local + backend)
                    val prefManager = PreferenceManager(context)
                    // #region agent log
                    val _logPayload = org.json.JSONObject().apply {
                        put("sessionId", "debug-session")
                        put("location", "ClimateStatusManager.getDeviceInfoOnChargingStart:beforeSaveDeviceInfo")
                        put("message", "saving device info to prefs")
                        put("data", org.json.JSONObject().apply { put("climateStatus", deviceInfo.climateStatus) })
                        put("timestamp", System.currentTimeMillis())
                        put("hypothesisId", "H4")
                    }.toString()
                    Log.d("ClimateNotifDebug", _logPayload)
                    // #endregion
                    prefManager.saveDeviceInfo(deviceInfo)
                    // #region agent log
                    Log.d("ClimateNotifDebug", org.json.JSONObject().apply {
                        put("sessionId", "debug-session")
                        put("location", "ClimateStatusManager.getDeviceInfoOnChargingStart:afterSaveDeviceInfo")
                        put("message", "saveDeviceInfo called (apply async)")
                        put("data", org.json.JSONObject().apply { put("climateStatus", deviceInfo.climateStatus) })
                        put("timestamp", System.currentTimeMillis())
                        put("hypothesisId", "H4")
                    }.toString())
                    // #endregion
                    
                    val hasStation = deviceInfo.stationInfo != null
                    prefManager.setHasStation(hasStation)
                    if (deviceInfo.stationInfo != null) {
                        prefManager.saveStationInfo(deviceInfo.stationInfo)
                        prefManager.saveStationCode(deviceInfo.stationInfo!!.stationCode)
                    }
                    
                    context.sendBroadcast(Intent(BatteryServiceActions.HAS_STATION_UPDATED))
                }
            } else {
                Log.w(TAG, "getDeviceInfo failed: ${response?.code() ?: "timeout"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceInfo error: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "getDeviceInfo")
                Sentry.captureException(e)
            }
        }
    }
}
