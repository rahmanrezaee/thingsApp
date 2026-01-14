package com.example.thingsappandroid.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Data class representing battery state
 */
data class BatteryState(
    val isCharging: Boolean,
    val voltage: Int, // in millivolts
    val level: Int, // battery level 0-100
    val plugged: Int // charging type
) {
    fun plugged(): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "UNPLUGGED"
        }
    }
}

/**
 * BatteryReceiver that observes battery state changes and emits them as a Flow
 */
object BatteryReceiver {
    fun observe(context: Context): Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    
                    val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) {
                        (level * 100 / scale)
                    } else {
                        0
                    }
                    
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    
                    trySend(BatteryState(
                        isCharging = isCharging,
                        voltage = voltage,
                        level = batteryPct,
                        plugged = plugged
                    ))
                }
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        
        // Get initial battery state
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            
            val voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                0
            }
            
            val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            
            trySend(BatteryState(
                isCharging = isCharging,
                voltage = voltage,
                level = batteryPct,
                plugged = plugged
            ))
        }
        
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
    }
}
