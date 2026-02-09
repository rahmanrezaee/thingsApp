package com.example.thingsappandroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.thingsappandroid.data.local.entity.BatteryReadingEntity

@Dao
interface BatteryReadingDao {

    @Insert
    suspend fun insert(reading: BatteryReadingEntity): Long

    @Query("SELECT * FROM battery_readings WHERE groupId = :groupId ORDER BY startTime ASC")
    suspend fun getByGroup(groupId: String): List<BatteryReadingEntity>

    @Query("SELECT * FROM battery_readings ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastReading(): BatteryReadingEntity?

    @Query("DELETE FROM battery_readings WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Query("SELECT DISTINCT groupId FROM battery_readings")
    suspend fun getAllGroupIds(): List<String>

    @Query("SELECT COUNT(*) FROM battery_readings WHERE groupId = :groupId")
    suspend fun getCountForGroup(groupId: String): Int
}
