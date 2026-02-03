package com.example.thingsappandroid.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log

object DeviceUtils {
    private const val TAG = "DeviceUtils"

    /**
     * Returns the device ID using Settings.Secure.ANDROID_ID (no store).
     *
     * @param context The application context
     * @return The Android ID string, or null if unavailable
     */
    @SuppressLint("HardwareIds")
    fun getStoredDeviceId(context: Context): String? {
        return try {
            val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!id.isNullOrEmpty()) id else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID: ${e.message}")
            null
        }
    }
}
