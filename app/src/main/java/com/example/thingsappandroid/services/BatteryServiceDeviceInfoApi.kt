package com.example.thingsappandroid.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.model.CarbonIntensityInfo
import com.example.thingsappandroid.data.model.DeviceInfoRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles getDeviceInfo API for BatteryService. Register/getToken are done only in SplashViewModel.
 */
class BatteryServiceDeviceInfoApi(
    private val context: Context,
    private val deviceId: String,
    private val preferenceManager: PreferenceManager,
    private val tokenManager: TokenManager,
    private val thingsRepository: ThingsRepository,
    private val onUpdated: () -> Unit,
    private val isCharging: () -> Boolean
) {
    private val tag = "BatteryServiceDeviceInfoApi"

    suspend fun applyDefaultDeviceInfo() {
        val defaultInfo = thingsRepository.createDefaultDeviceInfo(deviceId)
        preferenceManager.saveLastDeviceInfo(defaultInfo)
        preferenceManager.setHasStation(false)
        Log.d(tag, "applyDefaultDeviceInfo: saved default (offline)")
        sendDeviceInfoUpdatedBroadcast()
        onUpdated()
    }

    /** Grid intensity (gCO₂e) used for offline charging display. */
    private val offlineGridIntensity = 485.0

    /**
     * Offline charging: check cached carbon budget and set ClimateStatus + grid intensity accordingly.
     * If data exists: budget > 0 → ClimateStatus 8 (1.5°C aligned), else → 4 (Not Green); grid 485.
     * If no data → create new record with ClimateStatus 8, carbon battery 100% (500g), grid 485.
     */
    suspend fun applyOfflineChargingDeviceInfo() {
        val cached = preferenceManager.getLastDeviceInfo()
        val carbonInfo485 = CarbonIntensityInfo(offlineGridIntensity, "Offline", null)
        if (cached != null) {
            val budget = cached.remainingBudget ?: 0.0
            val newStatus = if (budget > 0) 8 else 4
            val updated = cached.copy(
                climateStatus = newStatus,
                carbonInfo = carbonInfo485
            )
            preferenceManager.saveLastDeviceInfo(updated)
            Log.d(tag, "applyOfflineChargingDeviceInfo: budget=$budget → climateStatus=$newStatus, grid=$offlineGridIntensity")
        } else {
            val defaultInfo = thingsRepository.createDefaultDeviceInfo(deviceId)
            preferenceManager.saveLastDeviceInfo(defaultInfo)
            preferenceManager.setHasStation(false)
            Log.d(tag, "applyOfflineChargingDeviceInfo: no data → created default (ClimateStatus 8, carbon 100%, grid $offlineGridIntensity)")
        }
        sendDeviceInfoUpdatedBroadcast()
        onUpdated()
    }

    /**
     * Fetch device info when WiFi+location are ready. Register/getToken are done in SplashViewModel.
     * If no token (e.g. app started without splash), apply default offline info.
     */
    suspend fun getDeviceInfoOnlineOrNoInternet() {
        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.d(tag, "runGetDeviceInfoOnce: no token (Splash handles register/getToken) - applying default")
            applyDefaultDeviceInfo()
        } else {

            Log.d("getDeviceInfo","call getDeviceInfoOnlineOrNoInternet")
            NetworkModule.setAuthToken(token)
            fetchDeviceInfo()
        }
    }

    suspend fun fetchDeviceInfo() {
        val wifiAddress = withContext(Dispatchers.IO) { WifiUtils.getHashedWiFiBSSID(context) }
        if (wifiAddress.isNullOrBlank()) {
            Log.d(tag, "fetchDeviceInfo: WiFi address missing - not sending request")
            if (isCharging()) {
                applyOfflineChargingDeviceInfo()
            } else {
                sendDeviceInfoUpdatedBroadcast()
                onUpdated()
            }
            return
        }
        val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
        val request = DeviceInfoRequest(
            deviceId = deviceId,
            wifiAddress = wifiAddress,
            currentVersion = "1.0.0",
            latitude = latitude,
            longitude = longitude
        )
        try {

            Log.d("getDeviceInfo","fetchDeviceInfo")
            val response = withTimeoutOrNull(12_000L) {
                withContext(Dispatchers.IO) { NetworkModule.api.getDeviceInfo(request) }
            }
            if (response != null && response.isSuccessful && response.body()?.data != null) {
                val deviceInfo = response.body()!!.data!!
                preferenceManager.saveLastDeviceInfo(deviceInfo)
                preferenceManager.setHasStation(deviceInfo.stationInfo != null)
                deviceInfo.stationInfo?.let {
                    preferenceManager.saveStationInfo(it)
                    preferenceManager.saveStationCode(it.stationCode)
                }
                preferenceManager.saveLastWifiBssid(wifiAddress)
                Log.d(tag, "fetchDeviceInfo: success")
            } else {
                Log.w(tag, "fetchDeviceInfo: failed code=${response?.code() ?: "timeout"}")
                if (isCharging()) {
                    Log.d(tag, "fetchDeviceInfo: no internet + charging → applying offline charging device info")
                    applyOfflineChargingDeviceInfo()
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchDeviceInfo: error ${e.message}", e)
            Sentry.captureException(e)
            if (isCharging()) {
                Log.d(tag, "fetchDeviceInfo: error while charging → applying offline charging device info")
                applyOfflineChargingDeviceInfo()
                return
            }
        }
        sendDeviceInfoUpdatedBroadcast()
        onUpdated()
    }

    fun sendDeviceInfoUpdatedBroadcast() {
        val intent = Intent(BatteryServiceActions.DEVICEINFO_UPDATED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
