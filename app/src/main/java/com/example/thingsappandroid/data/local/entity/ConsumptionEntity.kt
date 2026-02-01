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
    val createdAt: Long = System.currentTimeMillis()
)
