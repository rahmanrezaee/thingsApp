package com.example.thingsappandroid.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import java.security.MessageDigest

object WifiUtils {
    private const val TAG = "WifiUtils"

    /**
     * Gets the WiFi SSID (Service Set Identifier) of the currently connected network.
     * 
     * @param context The application context
     * @return The SSID string, or null if not available
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun getWiFiSSID(context: Context): String? {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo?.ssid

            Log.d(TAG, "WiFi SSID: ${ wifiInfo?.ssid}")
            Log.d(TAG, "WiFi bssid: ${simpleHash( wifiInfo?.bssid)}")
            Log.d(TAG, "WiFi wifiStandard: ${ wifiInfo?.wifiStandard}")
            Log.d(TAG, "WiFi rssi: ${ wifiInfo?.rssi}")
            Log.d(TAG, "WiFi hiddenSSID:  ${ wifiInfo?.hiddenSSID}")
//            ef8bw/5V8hPcJb3x+gpWPd1MFoYmW9GlknHEOZfBJLo=
//            ef8bw/5V8hPcJb3x+gpWPd1MFoYmW9GlknHEOZfBJLo=
            if (ssid != null && ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                // Remove quotes if present (Android sometimes returns SSID with quotes)
                val cleanSsid = ssid.removeSurrounding("\"")
                Log.d(TAG, "WiFi SSID: $cleanSsid")
                cleanSsid
            } else {
                Log.w(TAG, "No valid SSID found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi SSID: ${e.message}", e)
            null
        }
    }

    fun simpleHash(input: String?): String? {
        if (input.isNullOrEmpty()) {
            return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        // Remove any trailing whitespace/newlines just to be safe
        return encoded.trim()
    }

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

            Log.d(TAG, "Raw BSSID: $bssid")

            return simpleHash( wifiInfo?.bssid)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi BSSID: ${e.message}", e)
            null
        }
    }
}