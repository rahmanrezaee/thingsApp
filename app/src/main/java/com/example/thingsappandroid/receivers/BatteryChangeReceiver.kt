package com.example.thingsappandroid.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * Broadcast receiver that monitors battery state changes.
 * Detects battery level, charging status, and power connection events.
 */
class BatteryChangeReceiver : BroadcastReceiver() {

    private val TAG = "BatteryChangeReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED -> {

                // Get battery state from the intent
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) {
                    ((level * 100f) / scale).toInt()
                } else {
                    -1
                }

                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

                Log.d(TAG, "Battery changed - Level: $batteryPct%, Charging: $isCharging, Voltage: ${voltage}mV, Plugged: $plugged")

                // Broadcast to BatteryService and HomeViewModel
                val serviceIntent = Intent("com.example.thingsappandroid.BATTERY_CHANGED").apply {
                    setPackage(context.packageName)
                    putExtra("is_charging", isCharging)
                    putExtra("level", batteryPct)
                    putExtra("voltage", voltage)
                    putExtra("plugged", plugged)
                }
                context.sendBroadcast(serviceIntent)
                Log.d(TAG, "Broadcasted BATTERY_CHANGED to ${context.packageName}")
            }
        }
    }
}
