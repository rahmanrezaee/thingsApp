package com.example.thingsappandroid.services.utils

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.model.SetClimateStatusRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.services.API_GREEN_CLIMATE_STATUSES
import com.example.thingsappandroid.services.BatteryServiceActions
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages climate status API calls when charging starts.
 * Caller (BatteryService) must ensure WiFi and location are available before calling.
 * If a new request arrives while one is in progress, the old one is cancelled.
 */
class ClimateStatusManager(private val context: Context) {

    companion object {
        private const val TAG = "getDeviceInfo"
        private const val API_TIMEOUT_MS = 10_000L
        private const val WIFI_MAX_RETRIES = 3
        private const val WIFI_RETRY_DELAY_MS = 1500L
        private const val STATION_CODE_NOTIFICATION_ID = 2
    }

    private var currentJob: Job? = null

    /**
     * Calls SetClimateStatus then GetDeviceInfo. Updates preferences with result.
     * Cancels any in-progress request before starting a new one.
     *
     * @param stationCode Optional station code to send with SetClimateStatus (e.g. after user set station).
     * @return Climate status from API if successful, null otherwise
     */
    suspend fun handleChargingStarted(
        job: Job,
        stationCode: String? = null,
        onLocationError: ((reason: String, details: String, isPermissionDenied: Boolean, isServicesDisabled: Boolean) -> Unit)? = null
    ): Int? {
        // Cancel any previous in-flight request
        currentJob?.cancel()
        currentJob = job

        return try {
            val deviceId = DeviceUtils.getStoredDeviceId(context)
            if (deviceId.isNullOrBlank()) {
                Log.w(TAG, "Skipped: no deviceId")
                return null
            }

            if (NetworkModule.getAuthToken().isNullOrBlank()) {
                Log.d(TAG, "Skipped: no auth token")
                return null
            }

            val wifiResult = WifiUtils.getHashedWiFiBSSIDWithRetry(context, maxRetries = WIFI_MAX_RETRIES, delayMs = WIFI_RETRY_DELAY_MS)
            if (!wifiResult.success || wifiResult.bssid == null) {
                Log.w(TAG, "WiFi BSSID not available: ${wifiResult.errorReason}")
                return null
            }

            val hasPermission = LocationUtils.hasLocationPermission(context)
            val isEnabled = LocationUtils.isLocationEnabled(context)
            if (!hasPermission || !isEnabled) {
                onLocationError?.invoke(
                    if (!hasPermission) "Location permission denied" else "Location services disabled",
                    "Location required for carbon tracking.",
                    !hasPermission,
                    !isEnabled
                )
                return null
            }

            val climateStatus = callSetClimateStatus(deviceId, wifiResult.bssid, stationCode)
            callGetDeviceInfo(deviceId, wifiResult.bssid)
            climateStatus
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "handleChargingStarted")
                Sentry.captureException(e)
            }
            null
        } finally {
            if (currentJob == job) currentJob = null
        }
    }

    /**
     * Call SetClimateStatus API with DeviceId, WiFiAddress, Location, and optional StationCode.
     */
    private suspend fun callSetClimateStatus(deviceId: String, wiFiAddress: String, stationCode: String? = null): Int? {
        return try {
            val (lat, lng) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val request = SetClimateStatusRequest(
                deviceId = deviceId,
                latitude = lat,
                longitude = lng,
                wiFiAddress = wiFiAddress,
                stationCode = stationCode
            )

            val response = withTimeoutOrNull(API_TIMEOUT_MS) {
                NetworkModule.api.setClimateStatus(request)
            }

            if (response?.isSuccessful == true) {
                response.body()?.data?.climateStatus.also {
                    Log.d(TAG, "SetClimateStatus success: climateStatus=$it")
                }
            } else {
                Log.w(TAG, "SetClimateStatus failed: ${response?.code() ?: "timeout"}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "SetClimateStatus error: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "setClimateStatus")
                Sentry.captureException(e)
            }
            null
        }
    }

    /**
     * Call GetDeviceInfo API and save result
     */
    private suspend fun callGetDeviceInfo(deviceId: String, wiFiAddress: String) {
        try {
            val (lat, lng) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val request = DeviceInfoRequest(
                deviceId = deviceId,
                wifiAddress = wiFiAddress,
                latitude = lat,
                longitude = lng
            )

            val response = withTimeoutOrNull(API_TIMEOUT_MS) {
                NetworkModule.api.getDeviceInfo(request)
            }

            if (response?.isSuccessful != true) {
                Log.w(TAG, "GetDeviceInfo failed: ${response?.code() ?: "timeout"}")
                return
            }

            val deviceInfo = response.body()?.data ?: return

            val prefManager = PreferenceManager(context)
            prefManager.saveDeviceInfo(deviceInfo)
            prefManager.setHasStation(deviceInfo.stationInfo != null)
            deviceInfo.stationInfo?.let {
                prefManager.saveStationInfo(it)
                prefManager.saveStationCode(it.stationCode)
            }
            prefManager.saveLastWifiBssid(wiFiAddress)

            // Dismiss station code notification if climate status is green
            val climateStatus = deviceInfo.climateStatus
            if (climateStatus != null && climateStatus in API_GREEN_CLIMATE_STATUSES) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(STATION_CODE_NOTIFICATION_ID)
            }

            // Broadcast so HomeViewModel & BatteryService update UI
            context.sendBroadcast(
                Intent(BatteryServiceActions.DEVICEINFO_UPDATED).apply {
                    setPackage(context.packageName)
                }
            )

            Log.d(TAG, "GetDeviceInfo success")
        } catch (e: Exception) {
            Log.e(TAG, "GetDeviceInfo error: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "getDeviceInfo")
                Sentry.captureException(e)
            }
        }
    }
}