package com.example.thingsappandroid.services

import android.os.BatteryManager

/** Battery status from BatteryManager.EXTRA_STATUS. */
enum class BatteryStatus {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN
}

/** Battery health from BatteryManager.EXTRA_HEALTH. */
enum class BatteryHealth {
    COLD,
    DEAD,
    GOOD,
    OVERHEAT,
    OVER_VOLTAGE,
    UNSPECIFIED_FAILURE,
    UNKNOWN
}

/**
 * Represents the current battery state from system.
 */
data class BatteryState(
    val isCharging: Boolean,
    val voltage: Int, // in millivolts
    val level: Int, // battery level 0-100
    val plugged: Int, // charging type
    val temperature: Int = -1, // tenths of Celsius, -1 if unknown
    val health: Int = -1, // BatteryManager health code
    val scale: Int = 100,
    val technology: String? = null,
    val statusCode: Int = -1 // BatteryManager.EXTRA_STATUS
) {
    fun plugged(): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
        else -> "UNPLUGGED"
    }

    fun status(): BatteryStatus = when (statusCode) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        else -> BatteryStatus.UNKNOWN
    }

    fun health(): BatteryHealth = when (health) {
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
        else -> BatteryHealth.UNKNOWN
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
