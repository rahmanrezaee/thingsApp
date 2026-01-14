package com.example.thingsappandroid.util

import android.os.BatteryManager
import android.os.Build

object BatteryUtil {
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
}
