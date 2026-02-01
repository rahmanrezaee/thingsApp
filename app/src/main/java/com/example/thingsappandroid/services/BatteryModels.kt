package com.example.thingsappandroid.services

import android.os.BatteryManager

/**
 * Represents the current battery state from system.
 */
data class BatteryState(
    val isCharging: Boolean,
    val voltage: Int, // in millivolts
    val level: Int, // battery level 0-100
    val plugged: Int // charging type
) {
    fun plugged(): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
        else -> "UNPLUGGED"
    }
}

/**
 * Device Climate Status levels based on battery conditions and charging state.
 */
enum class DeviceClimateStatus {
    EXCELLENT,  // Green energy charging with optimal conditions
    GOOD,       // Normal charging with good conditions
    NORMAL,     // Normal operation
    WARNING,    // Elevated temperature or suboptimal conditions
    CRITICAL,   // Overheating or critical battery issues
    UNKNOWN     // Status not yet determined
}

/**
 * Device Climate Status information for notification display.
 */
data class DCSInfo(
    val iconRes: Int,
    val color: Int,
    val title: String,
    val description: String,
    val detailedDescription: String
)
