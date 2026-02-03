package com.example.thingsappandroid.services.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.model.SetClimateStatusRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import io.sentry.Sentry

/**
 * Monitors WiFi address changes and handles appropriate actions
 * based on device charging state.
 */
class WifiAddressMonitor(private val context: Context) {
    
    private val TAG = "WifiAddressMonitor"
    private var lastKnownWifiAddress: String? = null
    
    /**
     * Check if WiFi address has changed
     */
    fun hasWifiChanged(currentWifiAddress: String?): Boolean {
        return lastKnownWifiAddress != null && 
               lastKnownWifiAddress != currentWifiAddress
    }
    
    /**
     * Update the last known WiFi address
     */
    fun updateLastKnownAddress(wifiAddress: String?) {
        lastKnownWifiAddress = wifiAddress
    }
    
    /**
     * Get the last known WiFi address
     */
    fun getLastKnownAddress(): String? = lastKnownWifiAddress
    
    /**
     * Handle WiFi address change based on charging state
     * 
     * If charging:
     *   - Call SetClimateStatus
     *   - Call GetDeviceInfo
     * 
     * If not charging:
     *   - Call GetGreenFiInfo
     *   - Update Station UI
     */
    suspend fun handleWifiAddressChange(
        deviceId: String?,
        wifiAddress: String?,
        latitude: Double?,
        longitude: Double?,
        isCharging: Boolean
    ) {
        if (deviceId == null || wifiAddress == null) {
            Log.w(TAG, "Cannot handle WiFi change - missing deviceId or wifiAddress")
            return
        }
        
        try {
            if (isCharging) {
                handleWifiChangeWhileCharging(deviceId, wifiAddress, latitude, longitude)
            } else {
                handleWifiChangeWhileNotCharging(deviceId, wifiAddress, latitude, longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WiFi address change", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "handleWifiAddressChange")
                scope.setTag("is_charging", isCharging.toString())
                Sentry.captureException(e)
            }
        }
    }
    
    /**
     * Handle WiFi change when device is charging
     */
    private suspend fun handleWifiChangeWhileCharging(
        deviceId: String,
        wifiAddress: String,
        latitude: Double?,
        longitude: Double?
    ) {
        Log.d(TAG, "📱 Device is CHARGING - Calling SetClimateStatus + GetDeviceInfo")
        
        // Call SetClimateStatus
        val request = SetClimateStatusRequest(
            deviceId = deviceId,
            latitude = latitude ?: 0.0,
            longitude = longitude ?: 0.0,
            wiFiAddress = wifiAddress
        )
        
        val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
            NetworkModule.api.setClimateStatus(request)
        }
        
        if (response?.isSuccessful == true) {
            response.body()?.data?.let { data ->
                Log.d(TAG, "✅ SetClimateStatus success: climateStatus=${data.climateStatus}")
            }
        } else {
            Log.w(TAG, "⚠️ SetClimateStatus failed: ${response?.code() ?: "timeout"}")
        }
        
        // Call GetDeviceInfo
        val deviceInfoRequest = DeviceInfoRequest(
            deviceId = deviceId,
            wifiAddress = wifiAddress,
            latitude = latitude,
            longitude = longitude
        )
        
        val deviceInfoResponse = kotlinx.coroutines.withTimeoutOrNull(10000) {
            NetworkModule.api.getDeviceInfo(deviceInfoRequest)
        }
        
        if (deviceInfoResponse?.isSuccessful == true) {
            deviceInfoResponse.body()?.data?.let { deviceInfo ->
                Log.d(TAG, "✅ GetDeviceInfo successful")
                PreferenceManager(context).saveDeviceInfo(deviceInfo)
                
                deviceInfo.stationInfo?.let { stationInfo ->
                    PreferenceManager(context).saveStationInfo(stationInfo)
                }
            }
        } else {
            Log.w(TAG, "⚠️ GetDeviceInfo failed: ${deviceInfoResponse?.code() ?: "timeout"}")
        }
    }
    
    /**
     * Handle WiFi change when device is NOT charging
     */
    private suspend fun handleWifiChangeWhileNotCharging(
        deviceId: String,
        wifiAddress: String,
        latitude: Double?,
        longitude: Double?
    ) {
        Log.d(TAG, "🔌 Device is NOT CHARGING - Calling GetGreenFiInfo")
        
        // Call GetGreenFiInfo
        val greenFiRequest = com.example.thingsappandroid.data.model.GetGreenFiInfoRequest(
            deviceId = deviceId,
            wifiAddress = wifiAddress,
            latitude = latitude,
            longitude = longitude
        )
        
        val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
            NetworkModule.api.getGreenFiInfo(greenFiRequest)
        }
        
        if (response?.isSuccessful == true) {
            response.body()?.let { greenFiData ->
                Log.d(TAG, "✅ GetGreenFiInfo success")
                
                // Save station info
                greenFiData.stationInfo?.let { stationInfo ->
                    PreferenceManager(context).saveStationInfo(stationInfo)
                    Log.d(TAG, "📍 Updated Station: ${stationInfo.stationName}, Green: ${stationInfo.isGreen}")
                    
                    // Broadcast to update UI
                    val updateIntent = Intent("com.example.thingsappandroid.STATION_INFO_UPDATED").apply {
                        putExtra("station_name", stationInfo.stationName)
                        putExtra("is_green", stationInfo.isGreen)
                        putExtra("station_code", stationInfo.stationCode)
                    }
                    context.sendBroadcast(updateIntent)
                }
            }
        } else {
            Log.w(TAG, "⚠️ GetGreenFiInfo failed: ${response?.code() ?: "timeout"}")
        }
    }
}
