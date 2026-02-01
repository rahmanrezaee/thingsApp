package com.example.thingsappandroid.services.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.model.SetClimateStatusRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.delay

/**
 * Manages climate status operations including:
 * - Setting climate status when charging starts
 * - Getting device info
 * - Validating WiFi and location availability
 */
class ClimateStatusManager(private val context: Context) {
    
    private val TAG = "ClimateStatusManager"
    
    /**
     * Handle charging started event
     * 
     * @return Climate status if successful, null otherwise
     */
    suspend fun handleChargingStarted(): Int? {
        return try {
            delay(2000) // Wait for system to stabilize
            
            val deviceId = DeviceUtils.getStoredDeviceId(context)
            
            // CRITICAL: Check WiFi and Location availability FIRST
            val wifiResult = WifiUtils.getHashedWiFiBSSIDWithRetry(
                context,
                maxRetries = 3,
                delayMs = 1500L
            )
            
            // Check location services status first
            val hasLocationPermission = LocationUtils.hasLocationPermission(context)
            val isLocationEnabled = LocationUtils.isLocationEnabled(context)
            
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val hasValidLocation = latitude != 0.0 || longitude != 0.0
            
            // Validate availability
            if (!wifiResult.success) {
                Log.w(TAG, "⚠️ WiFi BSSID not available: ${wifiResult.errorReason}")
                Log.w(TAG, "Details: ${wifiResult.errorDetails}")
                
                // Broadcast WiFi error to HomeViewModel
                val errorIntent = Intent("com.example.thingsappandroid.WIFI_STATUS_CHANGED").apply {
                    putExtra("is_connected", false)
                    putExtra("error_reason", wifiResult.errorReason)
                    putExtra("error_details", wifiResult.errorDetails)
                }
                context.sendBroadcast(errorIntent)
                return null
            }
            
            // Check location availability and broadcast specific error
            if (!hasLocationPermission || !isLocationEnabled || !hasValidLocation) {
                val errorReason = when {
                    !hasLocationPermission -> "Location permission denied"
                    !isLocationEnabled -> "Location services disabled"
                    !hasValidLocation -> "Unable to get location coordinates"
                    else -> "Location not available"
                }
                val errorDetails = when {
                    !hasLocationPermission -> "Please grant location permission in app settings to enable carbon tracking."
                    !isLocationEnabled -> "Please enable location services (GPS) in your device settings. Location is required for WiFi identification and carbon tracking."
                    !hasValidLocation -> "Unable to retrieve your current location. Please check your GPS signal."
                    else -> "Location services are not available."
                }
                
                Log.w(TAG, "⚠️ Location not available: $errorReason")
                
                // Broadcast location error to BatteryService to show notification
                val locationErrorIntent = Intent("com.example.thingsappandroid.LOCATION_ERROR").apply {
                    putExtra("error_reason", errorReason)
                    putExtra("error_details", errorDetails)
                    putExtra("is_permission_denied", !hasLocationPermission)
                    putExtra("is_services_disabled", !isLocationEnabled)
                }
                context.sendBroadcast(locationErrorIntent)
                return null
            }
            
            val currentWifiAddress = wifiResult.bssid
            Log.d(TAG, "✅ WiFi & Location available - WiFi: ${currentWifiAddress?.take(10)}..., Location: ($latitude, $longitude)")
            
            // Call SetClimateStatus
            setClimateStatusOnChargingStart(deviceId, currentWifiAddress)
            
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
                    climateStatusInt?.let { 
                        PreferenceManager(context).saveClimateStatus(it) 
                    }
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
                    
                    // Save to PreferenceManager
                    val prefManager = PreferenceManager(context)
                    prefManager.saveDeviceInfo(deviceInfo)
                    
                    // Save station info if available
                    deviceInfo.stationInfo?.let { stationInfo ->
                        prefManager.saveStationInfo(stationInfo)
                        prefManager.saveStationCode(stationInfo.stationCode)
                        
                        // Broadcast that device has station
                        val intent = Intent("com.example.thingsappandroid.HAS_STATION_UPDATED")
                        context.sendBroadcast(intent)
                    }
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
