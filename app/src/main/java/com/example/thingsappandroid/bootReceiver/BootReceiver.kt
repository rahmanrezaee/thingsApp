package com.example.thingsappandroid.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.thingsappandroid.data.local.PreferenceManager

/**
 * BroadcastReceiver that starts BatteryService when the device boots up, only if we have cached device info.
 * First-time install: no cache, so we don't start; user opens app, Home loads getDeviceInfo and caches, then starts service.
 */
class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val hasCachedDeviceInfo = PreferenceManager(context).getLastDeviceInfo() != null
            if (!hasCachedDeviceInfo) {
                Log.d(TAG, "Device booted, no cached device info; BatteryService will start when user opens app and Home caches getDeviceInfo.")
                return
            }
            Log.d(TAG, "Device booted, starting BatteryService (cached device info present)")
            val serviceIntent = Intent(context, BatteryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}