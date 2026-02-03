package com.example.thingsappandroid.data.local

import android.content.Context
import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.data.model.StationInfo
import com.google.gson.Gson

class PreferenceManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit().putString("device_id", deviceId).apply()
    }

    fun getDeviceId(): String? {
        return sharedPreferences.getString("device_id", null)
    }

    fun getThemeMode(): String {
        return sharedPreferences.getString("theme_mode", "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        sharedPreferences.edit().putString("theme_mode", mode).apply()
    }

    /** True when device has a station from getDeviceInfo (StationInfo). Used by BatteryService to avoid showing Station Code notification when already connected. */
    fun getHasStation(): Boolean {
        return sharedPreferences.getBoolean("has_station", false)
    }

    fun setHasStation(hasStation: Boolean) {
        sharedPreferences.edit().putBoolean("has_station", hasStation).apply()
    }

    /** Saves the last loaded device info for offline use. */
    fun saveDeviceInfo(deviceInfo: DeviceInfoResponse?) {
        saveLastDeviceInfo(deviceInfo)
    }

    /** Saves the last loaded device info for offline use. */
    fun saveLastDeviceInfo(deviceInfo: DeviceInfoResponse?) {
        if (deviceInfo == null) {
            sharedPreferences.edit().remove("last_device_info").apply()
        } else {
            sharedPreferences.edit()
                .putString("last_device_info", gson.toJson(deviceInfo))
                .apply()
            
            // Also save station info and carbon intensity if available
            deviceInfo.stationInfo?.let { saveStationInfo(it) }
            deviceInfo.carbonInfo?.let { saveCarbonIntensity(it.currentIntensity.toInt()) }
        }
    }

    /** Returns the last saved device info, or null if never loaded. */
    fun getLastDeviceInfo(): DeviceInfoResponse? {
        val json = sharedPreferences.getString("last_device_info", null) ?: return null
        return try {
            gson.fromJson(json, DeviceInfoResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveStationInfo(stationInfo: StationInfo?) {
        if (stationInfo == null) {
            sharedPreferences.edit().remove("station_info").apply()
        } else {
            sharedPreferences.edit().putString("station_info", gson.toJson(stationInfo)).apply()
        }
    }

    fun getStationInfo(): StationInfo? {
        val json = sharedPreferences.getString("station_info", null) ?: return null
        return try {
            gson.fromJson(json, StationInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveStationCode(stationCode: String?) {
        sharedPreferences.edit().putString("station_code", stationCode).apply()
    }

    fun getStationCode(): String? {
        return sharedPreferences.getString("station_code", null)
    }

    fun saveCarbonIntensity(intensity: Int) {
        sharedPreferences.edit().putInt("carbon_intensity", intensity).apply()
    }

    fun getCarbonIntensity(): Int? {
        val v = sharedPreferences.getInt("carbon_intensity", -1)
        return if (v < 0) null else v
    }

    /** True if user skipped the "turn on location" request on splash. */
    fun getLocationRequestSkipped(): Boolean {
        return sharedPreferences.getBoolean("location_request_skipped", false)
    }

    fun setLocationRequestSkipped(skipped: Boolean) {
        sharedPreferences.edit().putBoolean("location_request_skipped", skipped).apply()
    }

    /** Last known WiFi BSSID (hashed). Used to detect WiFi changes and trigger device info refresh. */
    fun getLastWifiBssid(): String? = sharedPreferences.getString("last_wifi_bssid", null)

    fun saveLastWifiBssid(bssid: String?) {
        if (bssid == null) {
            sharedPreferences.edit().remove("last_wifi_bssid").apply()
        } else {
            sharedPreferences.edit().putString("last_wifi_bssid", bssid).apply()
        }
    }
}
