package com.example.thingsappandroid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.thingsappandroid.data.local.entity.BatteryReadingEntity
import com.example.thingsappandroid.data.local.model.GroupedBatteryData

@Dao
interface BatteryReadingDao {

    @Insert
    suspend fun insert(reading: BatteryReadingEntity): Long

    @Query("SELECT * FROM battery_readings WHERE groupId = :groupId ORDER BY startTime ASC")
    suspend fun getByGroup(groupId: String): List<BatteryReadingEntity>

    /**
     * Get aggregated battery data for each group (charging session).
     * Calculates total watt-hours (Wh) used, start time, and end time per groupId.
     * Conversion: Wh = Watt * (duration_ms / 3,600,000)
     */
    @Query("""
        SELECT 
            groupId, 
            SUM(ABS(watt) * (CAST(endTime AS REAL) - startTime) / 3600000.0) as totalWattHours, 
            MIN(startTime) as startTime, 
            MAX(endTime) as endTime,
            (SELECT level FROM battery_readings b2 WHERE b2.groupId = b1.groupId ORDER BY startTime ASC LIMIT 1) as startLevel,
            (SELECT level FROM battery_readings b2 WHERE b2.groupId = b1.groupId ORDER BY startTime DESC LIMIT 1) as endLevel
        FROM battery_readings b1
        GROUP BY groupId
    """)
    suspend fun getGroupedBatteryData(): List<GroupedBatteryData>

    /**
     * Get aggregated battery data for each group within a time range.
     */
    @Query("""
        SELECT 
            groupId, 
            SUM(ABS(watt) * (CAST(endTime AS REAL) - startTime) / 3600000.0) as totalWattHours, 
            MIN(startTime) as startTime, 
            MAX(endTime) as endTime,
            (SELECT level FROM battery_readings b2 WHERE b2.groupId = b1.groupId ORDER BY startTime ASC LIMIT 1) as startLevel,
            (SELECT level FROM battery_readings b2 WHERE b2.groupId = b1.groupId ORDER BY startTime DESC LIMIT 1) as endLevel
        FROM battery_readings b1
        WHERE startTime BETWEEN :from AND :to
        GROUP BY groupId
    """)
    suspend fun getGroupedBatteryDataBetween(from: Long, to: Long): List<GroupedBatteryData>

    @Query("SELECT * FROM battery_readings ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastReading(): BatteryReadingEntity?

    @Query("SELECT DISTINCT groupId FROM battery_readings")
    suspend fun getAllGroupIds(): List<String>

    @Query("SELECT COUNT(*) FROM battery_readings WHERE groupId = :groupId")
    suspend fun getCountForGroup(groupId: String): Int

    /**
     * Get distinct groupIds whose readings have the given uploadStatus values.
     * Useful for finding sessions that are pending or failed.
     */
    @Query("SELECT DISTINCT groupId FROM battery_readings WHERE uploadStatus IN (:statuses)")
    suspend fun getGroupIdsByStatus(statuses: List<String>): List<String>

    /**
     * Update upload status for all readings in a session (groupId).
     */
    @Query(
        "UPDATE battery_readings SET uploadStatus = :status, uploadedAt = :uploadedAt, lastUploadError = :error WHERE groupId = :groupId"
    )
    suspend fun updateStatusForGroup(
        groupId: String,
        status: String,
        uploadedAt: Long?,
        error: String?
    )

    /** Optional: fetch readings in a time range, for export. */
    @Query(
        "SELECT * FROM battery_readings WHERE startTime BETWEEN :from AND :to ORDER BY startTime ASC"
    )
    suspend fun getReadingsBetween(from: Long, to: Long): List<BatteryReadingEntity>
}
