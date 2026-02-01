package com.example.thingsappandroid.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.thingsappandroid.util.WifiUtils

/**
 * Broadcast receiver that monitors WiFi state changes.
 * Detects WiFi connect/disconnect events and BSSID changes.
 */
class WiFiChangeReceiver : BroadcastReceiver() {
    
    private val TAG = "WiFiChangeReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
            WifiManager.WIFI_STATE_CHANGED_ACTION,
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                
                val isConnected = isWiFiConnected(context)
                val bssid = if (isConnected) {
                    WifiUtils.getHashedWiFiBSSID(context)
                } else {
                    null
                }
                
                Log.d(TAG, "WiFi state changed - Connected: $isConnected, BSSID: ${bssid?.take(10)}...")
                
                // Broadcast to BatteryService
                val serviceIntent = Intent("com.example.thingsappandroid.WIFI_CHANGED").apply {
                    putExtra("is_connected", isConnected)
                    putExtra("bssid", bssid)
                }
                context.sendBroadcast(serviceIntent)
            }
        }
    }
    
    /**
     * Checks if device is connected to WiFi.
     */
    private fun isWiFiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
}
