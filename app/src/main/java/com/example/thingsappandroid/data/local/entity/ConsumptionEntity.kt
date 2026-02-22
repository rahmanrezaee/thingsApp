package com.example.thingsappandroid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing consumption data locally when offline.
 * The model is stored as JSON and converted via TypeConverter.
 */
@Entity(tableName = "pending_consumption")
data class ConsumptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Upload state for this aggregated record. */
    val uploadStatus: String = "PENDING",
    /** Set when the record is successfully uploaded. */
    val uploadedAt: Long? = null,
    /** Last error message if upload failed. */
    val lastUploadError: String? = null,
    /** Optional server measurementId (if returned). */
    val measurementId: String? = null
)
