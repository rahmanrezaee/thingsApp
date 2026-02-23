package com.example.thingsappandroid.data.local.model

/**
 * Data class representing aggregated battery consumption for a specific group (charging session).
 */
data class GroupedBatteryData(
    val groupId: String,
    val totalWattHours: Double,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int
)
