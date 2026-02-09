package com.example.thingsappandroid.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
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
    private val onUpdated: () -> Unit
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

    /**
     * Fetch device info when WiFi+location are ready. Register/getToken are done in SplashViewModel.
     * If no token (e.g. app started without splash), apply default offline info.
     */
    suspend fun runGetDeviceInfoOnce() {
        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.d(tag, "runGetDeviceInfoOnce: no token (Splash handles register/getToken) - applying default")
            applyDefaultDeviceInfo()
        } else {
            NetworkModule.setAuthToken(token)
            fetchDeviceInfo(null)
        }
    }

    suspend fun fetchDeviceInfo(stationCodeOverride: String?) {
        val wifiAddress = withContext(Dispatchers.IO) { WifiUtils.getHashedWiFiBSSID(context) }
        if (wifiAddress.isNullOrBlank()) {
            Log.d(tag, "fetchDeviceInfo: WiFi address missing - not sending request")
            sendDeviceInfoUpdatedBroadcast()
            onUpdated()
            return
        }
        val stationCode = stationCodeOverride ?: preferenceManager.getStationCode()
        val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
        val request = DeviceInfoRequest(
            deviceId = deviceId,
            stationCode = stationCode,
            wifiAddress = wifiAddress,
            currentVersion = "1.0.0",
            latitude = latitude,
            longitude = longitude
        )
        try {
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
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchDeviceInfo: error ${e.message}", e)
            Sentry.captureException(e)
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
