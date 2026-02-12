package com.example.thingsappandroid.services

/**
 * Broadcast action strings used by BatteryService and related components.
 * Centralized to avoid magic strings and ensure consistency across the app.
 */
object BatteryServiceActions {
    /** Broadcast after getDeviceInfo API call + save to local Preference. BatteryService uses this for updateNotification(). */
    const val DEVICEINFO_UPDATED = "com.example.thingsappandroid.DEVICEINFO_UPDATED"

    /** Sent when token was null but we just got it (first time). BatteryService calls SetClimateStatus if charging + WiFi + location ready. */
    const val FOR_NEW_DEVICE_CALL_CLIMATE_STATUS = "com.example.thingsappandroid.FOR_NEW_DEVICE_CALL_CLIMATE_STATUS"

    /** Request BatteryService to run getDeviceInfo (e.g. after setStation or user refresh). Send from HomeViewModel. */
    const val REQUEST_GET_DEVICE_INFO = "com.example.thingsappandroid.REQUEST_GET_DEVICE_INFO"
    /** Optional extra: station code (e.g. for SetClimateStatus after setStation, or getDeviceInfo request). String. */
    const val EXTRA_STATION_CODE = "station_code"

    /** Intent action to open station code dialog (e.g. from notification tap) */
    const val OPEN_STATION_CODE = "com.example.thingsappandroid.OPEN_STATION_CODE"
}

/** Notification channel and notification IDs for BatteryService. */
object BatteryServiceNotificationIds {
    const val CHANNEL_ID = "battery_monitoring_channel"
    const val NOTIFICATION_ID = 1
    const val STATION_CODE_CHANNEL_ID = "station_code_channel"
    const val STATION_CODE_NOTIFICATION_ID = 2
    const val STATION_CODE_GROUP_KEY = "com.example.thingsappandroid.station_code_group"
    const val CHARGING_STARTED_LOCATION_REQUEST_CODE = 102
    const val CHARGING_STARTED_LOCATION_NOTIFICATION_ID = 5
    const val LOCATION_ERROR_CHANNEL_ID = "location_error_channel"
    const val LOCATION_ERROR_NOTIFICATION_ID = 4
    const val LOCATION_ERROR_REQUEST_CODE = 101
    const val LOCATION_PERMISSION_CHANNEL_ID = "location_permission_channel"
    const val LOCATION_PERMISSION_NOTIFICATION_ID = 3
}

/** API climate status values that indicate green energy (no station code prompt needed). */
val API_GREEN_CLIMATE_STATUSES: List<Int> = listOf(5, 6, 7, 9)
