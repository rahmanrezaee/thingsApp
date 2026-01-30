package com.example.thingsappandroid.data.local

import android.content.Context

class PreferenceManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

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

    /** API ClimateStatus (0–9). Used by BatteryService so notification matches home (Green / Align / Not green). */
    fun getClimateStatus(): Int? {
        val v = sharedPreferences.getInt("climate_status", -1)
        return if (v < 0) null else v
    }

    fun saveClimateStatus(status: Int?) {
        if (status == null) {
            sharedPreferences.edit().remove("climate_status").apply()
        } else {
            sharedPreferences.edit().putInt("climate_status", status).apply()
        }
    }

    /** True when device has a station from getDeviceInfo (StationInfo). Used by BatteryService to avoid showing Station Code notification when already connected. */
    fun getHasStation(): Boolean {
        return sharedPreferences.getBoolean("has_station", false)
    }

    fun setHasStation(hasStation: Boolean) {
        sharedPreferences.edit().putBoolean("has_station", hasStation).apply()
    }
}