package com.example.thingsappandroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity
import com.example.thingsappandroid.data.local.entity.ConsumptionItemEntity

@Dao
interface ConsumptionDao {

    // --- Consumption (session) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumption(entity: ConsumptionEntity): Long

    @Query("SELECT * FROM consumptions WHERE id = :id")
    suspend fun getConsumptionById(id: Long): ConsumptionEntity?

    @Query("SELECT * FROM consumptions WHERE uploadStatus = :status ORDER BY startTime ASC")
    suspend fun getConsumptionsByStatus(status: String): List<ConsumptionEntity>

    @Query("SELECT * FROM consumptions ORDER BY startTime ASC")
    suspend fun getAllConsumptions(): List<ConsumptionEntity>

    @Query("SELECT * FROM consumptions WHERE startTime >= :from AND endTime <= :to ORDER BY startTime ASC")
    suspend fun getConsumptionsBetween(from: Long, to: Long): List<ConsumptionEntity>

    @Query(
        "UPDATE consumptions SET totalWatt = :totalWatt, totalWattHours = :totalWattHours, averageAmpere = :averageAmpere, averageVoltage = :averageVoltage, intervalSeconds = :intervalSeconds, totalSamples = :totalSamples, sourceType = :sourceType, startTime = :startTime, endTime = :endTime, startLevel = :startLevel, endLevel = :endLevel, uploadStatus = :status, uploadedAt = :uploadedAt WHERE id = :id"
    )
    suspend fun updateConsumptionSummary(
        id: Long,
        totalWatt: Double,
        totalWattHours: Double,
        averageAmpere: Double,
        averageVoltage: Double,
        intervalSeconds: Int,
        totalSamples: Int,
        sourceType: String?,
        startTime: Long,
        endTime: Long,
        startLevel: Int,
        endLevel: Int,
        status: String,
        uploadedAt: Long?
    )

    @Query(
        "UPDATE consumptions SET uploadStatus = :status, uploadedAt = :uploadedAt WHERE id = :id"
    )
    suspend fun updateConsumptionStatus(
        id: Long,
        status: String,
        uploadedAt: Long?
    )

    // --- Items ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ConsumptionItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ConsumptionItemEntity>): List<Long>

    @Query("SELECT * FROM consumption_items WHERE consumptionId = :consumptionId ORDER BY startTime ASC")
    suspend fun getItemsForConsumption(consumptionId: Long): List<ConsumptionItemEntity>

    @Query("DELETE FROM consumption_items WHERE consumptionId = :consumptionId")
    suspend fun deleteItemsForConsumption(consumptionId: Long)
}
