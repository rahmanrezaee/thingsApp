package com.example.thingsappandroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consumptions")
data class ConsumptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startLevel: Int,
    val endLevel: Int,
    val totalWatt: Double,
    val totalWattHours: Double,
    val averageAmpere: Double,
    val averageVoltage: Double,
    val intervalSeconds: Int,
    val totalSamples: Int,
    val sourceType: String?,
    val startTime: Long,
    val endTime: Long,
    /** Upload state for this record. */
    val uploadStatus: String = "PENDING",
    /** Set when the record is successfully uploaded. */
    val uploadedAt: Long? = null
)
