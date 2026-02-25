package com.example.thingsappandroid.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object BatteryUtil {
    /**
     * Retrieves the battery capacity in mAh (milliampere-hours).
     * Uses reflection to access the internal PowerProfile class.
     * @return Battery capacity in mAh as an Integer, or 5000 if unavailable.
     */
    fun getBatteryCapacity(context: Context): Int {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val batteryCapacity = powerProfileClass.getMethod("getAveragePower", String::class.java)
                .invoke(powerProfile, "battery.capacity") as? Double
            batteryCapacity?.toInt() ?: 5000
        } catch (e: ClassNotFoundException) {
            // PowerProfile class not found (some devices don't have it)
            // Removed Toast to avoid blocking main thread - just log
            android.util.Log.w("BatteryUtil", "Battery capacity - ClassNotFound (${Build.MANUFACTURER} ${Build.MODEL})")
            5000
        } catch (e: NoSuchMethodException) {
            // Method not available
            android.util.Log.w("BatteryUtil", "Battery capacity - NoSuchMethod (${Build.MANUFACTURER} ${Build.MODEL})")
            5000
        } catch (e: SecurityException) {
            // Security restrictions on Samsung/other devices
            android.util.Log.w("BatteryUtil", "Battery capacity - Security (${Build.MANUFACTURER} ${Build.MODEL})")
            5000
        } catch (e: Exception) {
            // Any other exception - fallback to default 5000 mAh
            android.util.Log.w("BatteryUtil", "Battery capacity - ${e.javaClass.simpleName}: ${e.message} (${Build.MANUFACTURER} ${Build.MODEL})")
            5000
        }
    }

    /**
     * Converts battery current (in microamperes or milliamperes) to amperes
     * Handles device-specific issues:
     * - Most devices report in microamperes (µA)
     * - Some devices (e.g., Sony, certain Samsung models) report in milliamperes (mA)
     * - Xiaomi devices report the sign incorrectly, so we invert it
     */
    fun getBatteryCurrentNowInAmperes(currentNow: Int): Double {
        if (currentNow == Int.MIN_VALUE || currentNow == 0) {
            return 0.0
        }
        
        val absCurrent = Math.abs(currentNow)
        // Heuristic: If value is > 50,000, it's almost certainly microamperes (µA)
        // If it's < 10,000, it's likely milliamperes (mA)
        // Standard phone charging is 500mA - 5000mA (0.5A - 5A)
        // In µA: 500,000 - 5,000,000
        // In mA: 500 - 5,000
        val isMicroAmperes = absCurrent > 10_000

        var currentAmperes = if (isMicroAmperes) {
            currentNow / 1_000_000.0
        } else {
            currentNow / 1_000.0
        }
        
        // Xiaomi devices report the sign incorrectly (inverted)
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            currentAmperes = -currentAmperes
        }
        
        return currentAmperes
    }
    
    /**
     * Calculates power in watts: P = V * I
     * @param currentNow Current in microamperes
     * @param voltage Voltage in millivolts
     * @return Power in watts
     */
    fun getBatteryCurrentNowInWatt(currentNow: Int, voltage: Int): Double {
        val currentAmperes = getBatteryCurrentNowInAmperes(currentNow)
        val voltageVolts = voltage / 1000.0 // Convert from millivolts to volts
        return currentAmperes * voltageVolts
    }
    
    /**
     * Converts battery capacity from mAh to mWh
     * Uses typical battery voltage of 3.7V (nominal voltage for most phone batteries)
     * @param capacityMah Battery capacity in mAh
     * @param voltageVolts Optional voltage in volts (defaults to 3.7V)
     * @return Battery capacity in mWh
     */
    fun convertMahToMwh(capacityMah: Int, voltageVolts: Double = 3.7): Int {
        return (capacityMah * voltageVolts).toInt()
    }
    
    /**
     * Gets battery capacity in mWh
     * @param context The application context
     * @return Battery capacity in mWh as an Integer
     */
    fun getBatteryCapacityMwh(context: Context): Int {
        val capacityMah = getBatteryCapacity(context)
        return convertMahToMwh(capacityMah)
    }
    
    /**
     * Checks if the app is ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * Returns an intent to request ignoring battery optimizations.
     */
    @SuppressLint("BatteryLife")
    fun getIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Data class holding battery information
     */
    data class BatteryInfo(
        val level: Int,           // Battery percentage (0-100)
        val isCharging: Boolean,  // Whether device is charging
        val voltage: Int,         // Voltage in millivolts
        val plugged: Int,         // Plugged state (AC, USB, etc.)
        val temperature: Int,     // Temperature in tenths of Celsius
        val health: Int           // Battery health status
    )
    
    /**
     * Gets current battery information directly from the system.
     * Uses registerReceiver with null receiver to get the current sticky broadcast.
     * @param context The application context
     * @return BatteryInfo object with current battery state, or null if unavailable
     */
    fun getBatteryInfo(context: Context): BatteryInfo? {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) {
                    ((level * 100f) / scale).toInt()
                } else {
                    -1
                }
                
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                
                val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                
                android.util.Log.d("BatteryUtil", "getBatteryInfo - Level: $batteryPct%, Charging: $isCharging, Voltage: ${voltage}mV")
                
                BatteryInfo(
                    level = batteryPct,
                    isCharging = isCharging,
                    voltage = voltage,
                    plugged = plugged,
                    temperature = temperature,
                    health = health
                )
            } else {
                android.util.Log.w("BatteryUtil", "Failed to get battery info - registerReceiver returned null")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("BatteryUtil", "Error getting battery info: ${e.message}", e)
            null
        }
    }
}
