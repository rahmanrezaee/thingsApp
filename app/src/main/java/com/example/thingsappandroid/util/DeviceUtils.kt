package com.example.thingsappandroid.util

import android.content.Context
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager

object DeviceUtils {
    private const val TAG = "DeviceUtils"

    /**
     * Gets the stored device ID from TokenManager first, then falls back to PreferenceManager.
     * 
     * @param context The application context
     * @return The device ID string, or null if not found
     */
    fun getStoredDeviceId(context: Context): String? {
        return try {
            val tokenManager = TokenManager(context)
            val deviceId = tokenManager.getDeviceId()
            if (!deviceId.isNullOrEmpty()) {
                deviceId
            } else {
                val preferenceManager = PreferenceManager(context)
                preferenceManager.getDeviceId()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID: ${e.message}")
            null
        }
    }
}
