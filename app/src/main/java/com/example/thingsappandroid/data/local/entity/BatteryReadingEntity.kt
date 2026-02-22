package com.example.thingsappandroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Raw battery reading stored at each measurement interval (e.g. every 10s) during a charging session.
 * Used to aggregate into AndroidMeasurementModel for upload (reference pattern).
 */
@Entity(tableName = "battery_readings")
data class BatteryReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val voltage: Int,
    val level: Int,
    val isCharging: Boolean,
    val source: String,
    val ampere: Double,
    val watt: Double,
    val startTime: Long,
    val endTime: Long,
    val temperature: Int? = null,
    val health: Int? = null,
    /** Upload state for the aggregated session this reading belongs to. */
    val uploadStatus: String = "PENDING",
    /** Set when the session is successfully uploaded. */
    val uploadedAt: Long? = null,
    /** Last error message if upload failed. */
    val lastUploadError: String? = null
)
