package com.example.thingsappandroid.services

/**
 * Broadcast action strings used by BatteryService and related components.
 * Centralized to avoid magic strings and ensure consistency across the app.
 */
object BatteryServiceActions {
    /** Broadcast after getDeviceInfo API call + save to local Preference. BatteryService uses this for updateNotification(). */
    const val DEVICEINFO_UPDATED = "com.example.thingsappandroid.DEVICEINFO_UPDATED"

    /** Intent action to open station code dialog (e.g. from notification tap) */
    const val OPEN_STATION_CODE = "com.example.thingsappandroid.OPEN_STATION_CODE"
}

/** API climate status values that indicate green energy (no station code prompt needed). */
val API_GREEN_CLIMATE_STATUSES: List<Int> = listOf(5, 6, 7, 9)
