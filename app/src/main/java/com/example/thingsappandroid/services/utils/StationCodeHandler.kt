package com.example.thingsappandroid.services.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.model.SetClimateStatusRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.services.BatteryServiceActions
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.delay

/**
 * Handles station code submission flow.
 * 
 * Complete flow:
 * 1. Call SetStation with station code
 * 2. If successful, call SetClimateStatus
 * 3. If ClimateStatus not in [5,6,7,9], show notification
 * 4. Call GetDeviceInfo and store in local DB
 */
class StationCodeHandler(private val context: Context) {
    
    private val TAG = "StationCodeHandler"
    
    /**
     * Handle station code submission from user
     * 
     * @param deviceId Device ID
     * @param stationCode Station code entered by user
     * @param onNotificationNeeded Callback when notification should be shown
     * @return Climate status if successful, null otherwise
     */
    suspend fun handleStationCodeSubmission(
        deviceId: String,
        stationCode: String,
        onNotificationNeeded: suspend () -> Unit
    ): Int? {
        try {
            Log.d(TAG, "🚀 Starting station code submission flow...")
            
            // Get WiFi and Location
            val wifiResult = WifiUtils.getHashedWiFiBSSIDWithRetry(
                context, 
                maxRetries = 2, 
                delayMs = 1000L
            )
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            
            if (!wifiResult.success || wifiResult.bssid == null) {
                Log.w(TAG, "⚠️ Cannot submit station code - WiFi not available")
                return null
            }
            
            if (latitude == null || longitude == null) {
                Log.w(TAG, "⚠️ Cannot submit station code - Location not available")
                return null
            }
            
            // Step 1: Call SetStation
            Log.d(TAG, "1️⃣ Calling SetStation with code: $stationCode")
            val setStationSuccess = callSetStation(deviceId, stationCode)
            
            if (!setStationSuccess) {
                Log.w(TAG, "❌ SetStation failed")
                return null
            }
            
            Log.d(TAG, "✅ SetStation successful")
            
            // Step 2: Call SetClimateStatus
            Log.d(TAG, "2️⃣ Calling SetClimateStatus")
            val climateStatus = callSetClimateStatus(
                deviceId, 
                wifiResult.bssid, 
                latitude, 
                longitude, 
                stationCode
            )
            
            // Step 3: Check if we need to show notification
            if (climateStatus != null && climateStatus !in listOf(5, 6, 7, 9)) {
                Log.d(TAG, "3️⃣ ClimateStatus=$climateStatus requires station code notification")
                delay(1000)
                onNotificationNeeded()
            } else {
                Log.d(TAG, "3️⃣ ClimateStatus=$climateStatus - No notification needed")
            }
            
            // Step 4: Call GetDeviceInfo and store in local DB
            Log.d(TAG, "4️⃣ Calling GetDeviceInfo")
            callGetDeviceInfo(deviceId, wifiResult.bssid, latitude, longitude)
            
            Log.d(TAG, "🎉 Station code submission flow completed successfully!")
            return climateStatus
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in station code submission flow", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "handleStationCodeSubmission")
                scope.setTag("station_code", stationCode)
                Sentry.captureException(e)
            }
            return null
        }
    }
    
    /**
     * Call SetStation API
     */
    private suspend fun callSetStation(deviceId: String, stationCode: String): Boolean {
        return try {
            val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
                NetworkModule.api.setStation(
                    com.example.thingsappandroid.data.model.SetStationRequest(
                        deviceId = deviceId,
                        stationCode = stationCode
                    )
                )
            }
            
            response?.isSuccessful == true
        } catch (e: Exception) {
            Log.e(TAG, "Error calling SetStation", e)
            false
        }
    }
    
    /**
     * Call SetClimateStatus API
     */
    private suspend fun callSetClimateStatus(
        deviceId: String,
        wifiAddress: String,
        latitude: Double,
        longitude: Double,
        stationCode: String
    ): Int? {
        return try {
            val request = SetClimateStatusRequest(
                deviceId = deviceId,
                latitude = latitude,
                longitude = longitude,
                wiFiAddress = wifiAddress,
                stationCode = stationCode
            )
            
            val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
                NetworkModule.api.setClimateStatus(request)
            }
            
            var climateStatus: Int? = null
            if (response?.isSuccessful == true) {
                response.body()?.data?.let { data ->
                    climateStatus = data.climateStatus
                    Log.d(TAG, "✅ SetClimateStatus success: climateStatus=$climateStatus")
                }
            } else {
                Log.w(TAG, "⚠️ SetClimateStatus failed: ${response?.code() ?: "timeout"}")
            }
            
            climateStatus
        } catch (e: Exception) {
            Log.e(TAG, "Error calling SetClimateStatus", e)
            null
        }
    }
    
    /**
     * Call GetDeviceInfo API and store in local DB
     */
    private suspend fun callGetDeviceInfo(
        deviceId: String,
        wifiAddress: String,
        latitude: Double,
        longitude: Double
    ) {
        try {
            val request = DeviceInfoRequest(
                deviceId = deviceId,
                wifiAddress = wifiAddress,
                latitude = latitude,
                longitude = longitude
            )
            
            val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
                NetworkModule.api.getDeviceInfo(request)
            }
            
            if (response?.isSuccessful == true) {
                response.body()?.data?.let { deviceInfo ->
                    Log.d(TAG, "✅ GetDeviceInfo successful")
                    
                    // Store in PreferenceManager (local storage)
                    val prefManager = PreferenceManager(context)
                    prefManager.saveDeviceInfo(deviceInfo)
                    prefManager.setHasStation(deviceInfo.stationInfo != null)
                    deviceInfo.stationInfo?.let { Log.d(TAG, "💾 Stored station info: ${it.stationName}") }
                    
                    // Broadcast after save – BatteryService checks Preference has_station
                    val successIntent = Intent(BatteryServiceActions.HAS_STATION_UPDATED)
                    context.sendBroadcast(successIntent)
                }
            } else {
                Log.w(TAG, "⚠️ GetDeviceInfo failed: ${response?.code() ?: "timeout"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling GetDeviceInfo", e)
        }
    }
}
