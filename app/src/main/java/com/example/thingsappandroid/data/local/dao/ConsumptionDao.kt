package com.example.thingsappandroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConsumptionEntity): Long

    @Query("SELECT * FROM pending_consumption ORDER BY createdAt ASC")
    fun getAllPending(): Flow<List<ConsumptionEntity>>

    @Query("SELECT * FROM pending_consumption ORDER BY createdAt ASC")
    suspend fun getAllPendingOnce(): List<ConsumptionEntity>

    /** Only rows that still need to be uploaded/retried. */
    @Query("SELECT * FROM pending_consumption WHERE uploadStatus IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getByStatusOnce(statuses: List<String>): List<ConsumptionEntity>

    @Query(
        "UPDATE pending_consumption SET uploadStatus = :status, uploadedAt = :uploadedAt, lastUploadError = :error, measurementId = :measurementId WHERE id = :id"
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        uploadedAt: Long?,
        error: String?,
        measurementId: String?
    )

    /** Optional: for export filtering by time range. */
    @Query("SELECT * FROM pending_consumption WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt ASC")
    suspend fun getBetween(from: Long, to: Long): List<ConsumptionEntity>

    @Query("SELECT COUNT(*) FROM pending_consumption")
    suspend fun getPendingCount(): Int
}
