package com.example.thingsappandroid.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.security.MessageDigest

object WifiUtils {
    private const val TAG = "WifiUtils"

    /**
     * Gets the hashed WiFi BSSID (Basic Service Set Identifier) of the currently connected network.
     * The BSSID is hashed using SHA-256 for privacy.
     * 
     * @param context The application context
     * @return The hashed BSSID string, or null if not available or invalid
     */
    fun getHashedWiFiBSSID(context: Context): String? {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val bssid = wifiInfo?.bssid

            if (bssid != null && bssid != "02:00:00:00:00:00") {
                // Hash the BSSID for privacy
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(bssid.toByteArray())
                hashBytes.joinToString("") { "%02x".format(it) }
            } else {
                Log.w(TAG, "No valid BSSID found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi BSSID: ${e.message}")
            null
        }
    }
}
