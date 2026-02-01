package com.example.thingsappandroid.services

/**
 * Broadcast action strings used by BatteryService and related components.
 */
object BatteryServiceActions {
    const val STATION_CODE_UPDATED = "com.example.thingsappandroid.STATION_CODE_UPDATED"
    const val HAS_STATION_UPDATED = "com.example.thingsappandroid.HAS_STATION_UPDATED"
    const val BATTERY_CHANGED = "com.example.thingsappandroid.BATTERY_CHANGED"
    const val WIFI_CHANGED = "com.example.thingsappandroid.WIFI_CHANGED"
    const val LOCATION_CHANGED = "com.example.thingsappandroid.LOCATION_CHANGED"
    const val LOCATION_ERROR = "com.example.thingsappandroid.LOCATION_ERROR"
    const val MANUAL_SYNC_REQUESTED = "com.example.thingsappandroid.MANUAL_SYNC_REQUESTED"
    const val BATTERY_STATE_REQUEST = "com.example.thingsappandroid.BATTERY_STATE_REQUEST"
    const val DEVICE_INFO_UPDATED = "com.example.thingsappandroid.DEVICE_INFO_UPDATED"
    const val OPEN_STATION_CODE = "com.example.thingsappandroid.OPEN_STATION_CODE"
}

/** API climate status values that indicate green energy (no station code prompt needed). */
val API_GREEN_CLIMATE_STATUSES: List<Int> = listOf(5, 6, 7, 9)
