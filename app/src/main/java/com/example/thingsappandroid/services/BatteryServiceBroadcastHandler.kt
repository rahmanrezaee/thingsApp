package com.example.thingsappandroid.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

/**
 * Broadcast registration and connectivity/battery intent helpers for BatteryService.
 */
object BatteryServiceBroadcastHandler {

    fun createReceiver(
        onDeviceInfoUpdated: () -> Unit,
        onForNewDeviceCallClimateStatus: () -> Unit,
        onRequestGetDeviceInfo: () -> Unit,
        onBatteryIntent: (Intent) -> Unit,
        onConnectivityAction: (Intent?) -> Unit
    ): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: ""
            val receivedIntent: Intent? = intent
            when (action) {
                BatteryServiceActions.DEVICEINFO_UPDATED -> onDeviceInfoUpdated()
                BatteryServiceActions.FOR_NEW_DEVICE_CALL_CLIMATE_STATUS -> onForNewDeviceCallClimateStatus()
                BatteryServiceActions.REQUEST_GET_DEVICE_INFO -> onRequestGetDeviceInfo()
                Intent.ACTION_BATTERY_CHANGED, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> receivedIntent?.let { onBatteryIntent(it) }
                WifiManager.NETWORK_STATE_CHANGED_ACTION, WifiManager.WIFI_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION,
                android.location.LocationManager.PROVIDERS_CHANGED_ACTION, android.location.LocationManager.MODE_CHANGED_ACTION -> onConnectivityAction(receivedIntent)
            }
        }
    }

    fun createIntentFilter(): IntentFilter = IntentFilter().apply {
        addAction(BatteryServiceActions.DEVICEINFO_UPDATED)
        addAction(BatteryServiceActions.FOR_NEW_DEVICE_CALL_CLIMATE_STATUS)
        addAction(BatteryServiceActions.REQUEST_GET_DEVICE_INFO)
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        addAction(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        addAction(android.location.LocationManager.MODE_CHANGED_ACTION)
    }

    /**
     * Uses RECEIVER_EXPORTED so that same-app broadcasts with custom actions
     * (e.g. FOR_NEW_DEVICE_CALL_CLIMATE_STATUS, DEVICEINFO_UPDATED) are delivered.
     * On Android 14+, RECEIVER_NOT_EXPORTED blocks same-app custom-action broadcasts.
     * Sender always uses setPackage(context.packageName), so only our app sends these.
     */
    fun registerReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    fun isWiFiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            ni?.type == ConnectivityManager.TYPE_WIFI && ni.isConnected
        }
    }


    fun getChargingStatusFromIntent(intent: Intent?): Boolean? {

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return null
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isWifiOrConnectivityAction(action: String?): Boolean =
        action in listOf(
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
            WifiManager.WIFI_STATE_CHANGED_ACTION,
            ConnectivityManager.CONNECTIVITY_ACTION
        )
}
