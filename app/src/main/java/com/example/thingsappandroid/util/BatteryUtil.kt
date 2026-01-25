package com.example.thingsappandroid.util

import android.content.Context
import android.os.BatteryManager
import android.os.Build
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
     * Converts battery current (in microamperes) to amperes
     * Handles device-specific issues:
     * - Xiaomi devices report the sign incorrectly, so we invert it
     * - Conversion: microamperes / 1,000,000 = amperes
     */
    fun getBatteryCurrentNowInAmperes(currentNow: Int): Double {
        if (currentNow == Int.MIN_VALUE || currentNow == 0) {
            return 0.0
        }
        
        // Convert microamperes to amperes: divide by 1,000,000
        var currentAmperes = currentNow / 1_000_000.0
        
        // Xiaomi devices report the sign incorrectly (inverted)
        // So we need to invert the sign for Xiaomi devices
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
}
