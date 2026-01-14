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
}