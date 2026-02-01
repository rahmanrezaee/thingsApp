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

    @Query("DELETE FROM pending_consumption WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_consumption WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM pending_consumption")
    suspend fun getPendingCount(): Int
}
