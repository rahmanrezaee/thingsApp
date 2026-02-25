package com.example.thingsappandroid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consumption_items",
    foreignKeys = [
        ForeignKey(
            entity = ConsumptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["consumptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["consumptionId"])]
)
data class ConsumptionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val consumptionId: Long,
    val voltage: Int,
    val level: Int,
    val source: String,
    val ampere: Double,
    val watt: Double,
    val startTime: Long,
    val endTime: Long
)
