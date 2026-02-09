package com.example.thingsappandroid.services.utils

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.thingsappandroid.data.local.AppDatabase
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.entity.BatteryReadingEntity
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity
import com.example.thingsappandroid.data.model.AndroidMeasurementModel
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.services.BatteryState
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import com.google.gson.Gson
import io.sentry.Sentry
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Handles consumption tracking during charging sessions.
 * Stores raw readings in Room every 10 seconds; every 10 minutes aggregates them
 * into one AndroidMeasurementModel and uploads (reference pattern).
 */
class ConsumptionTracker(
    private val context: Context,
    private val deviceId: String,
    private val batteryManager: BatteryManager
) {
    private val TAG = "ConsumptionTracker"
    private val database = AppDatabase.getInstance(context)
    private val readingDao = database.batteryReadingDao()
    private val gson = Gson()

    private var currentGroupId: String? = null
    private var lastBatchUploadTime: Long = System.currentTimeMillis()

    companion object {
        const val CONSUMPTION_INTERVAL_MS = 10 * 1000L
        private const val BATCH_UPLOAD_INTERVAL_MS = 10 * 60 * 1000L
        private const val INTERVAL_SEC = 10
        private const val MIN_READINGS_TO_UPLOAD = 3
    }

    /**
     * Start tracking consumption when charging begins.
     * @param groupId UUID for this charging session (readings stored under this group).
     */
    fun startTracking(groupId: String, level: Int, voltage: Int) {
        currentGroupId = groupId
        lastBatchUploadTime = System.currentTimeMillis()
        Log.d(TAG, "Started consumption tracking - GroupId: $groupId, Level: $level%, Voltage: ${voltage}mV")
    }

    /**
     * Stop tracking when charging stops. Aggregates any remaining readings and uploads.
     */
    suspend fun stopTracking(level: Int, voltage: Int) {
        if (currentGroupId != null) {
            aggregateAndUpload()
            resetTracking()
            Log.d(TAG, "Stopped consumption tracking and uploaded final batch")
        }
    }

    /**
     * Record one raw reading (called every 10s). Inserts into Room; optionally triggers aggregate + upload if 10 min passed.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun recordReading(state: BatteryState) {
        val groupId = currentGroupId ?: return
        try {
            val currentNow = try {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (e: Exception) {
                Int.MIN_VALUE
            }
            val ampere = BatteryUtil.getBatteryCurrentNowInAmperes(currentNow)
            val watt = BatteryUtil.getBatteryCurrentNowInWatt(currentNow, state.voltage)
            val now = System.currentTimeMillis()
            val endTime = now + (INTERVAL_SEC * 1000L)

            val reading = BatteryReadingEntity(
                groupId = groupId,
                voltage = state.voltage,
                level = state.level,
                isCharging = state.isCharging,
                source = state.plugged(),
                ampere = ampere,
                watt = watt,
                startTime = now,
                endTime = endTime,
                temperature = state.temperature.takeIf { it >= 0 },
                health = state.health.takeIf { it >= 0 }
            )
            readingDao.insert(reading)
            Log.d(TAG, "Recorded reading: ${String.format("%.3f", watt)}W, ${state.level}%, group=$groupId")
            // Upload only on plug-out (in stopTracking), not during charging
        } catch (e: Exception) {
            Log.e(TAG, "Error recording reading", e)
        }
    }

    /**
     * Aggregate all readings for the current group from Room, build one AndroidMeasurementModel, upload (or save to pending). Then delete readings.
     */
    suspend fun aggregateAndUpload() {
        val groupId = currentGroupId ?: return
        try {
            val readings = readingDao.getByGroup(groupId)
            if (readings.size < MIN_READINGS_TO_UPLOAD) {
                Log.d(TAG, "Skipping upload - only ${readings.size} readings (need >= $MIN_READINGS_TO_UPLOAD)")
                return
            }

            val first = readings.first()
            val last = readings.last()
            val averageVoltage = readings.map { it.voltage / 1000.0 }.average()
            val averageAmpere = readings.map { it.ampere }.average()
            val totalWatts = readings.sumOf { it.watt }
            val totalWattHours = readings.sumOf { it.watt * (INTERVAL_SEC / 3600.0) }

            val batteryCapacityMah = try {
                val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (chargeCounter > 0) chargeCounter / 1000 else BatteryUtil.getBatteryCapacity(context)
            } catch (e: Exception) {
                BatteryUtil.getBatteryCapacity(context)
            }

            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val preferenceManager = PreferenceManager(context)
            val wifiBssid = WifiUtils.getHashedWiFiBSSID(context)
            val stationCode = preferenceManager.getStationCode()
            val stationInfo = preferenceManager.getStationInfo()
            val emissionFactor = preferenceManager.getCarbonIntensity() ?: 485
            val gramsCO2 = (totalWattHours / 1000.0) * emissionFactor

            val fromTime = Instant.ofEpochMilli(first.startTime).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
            val toTime = Instant.ofEpochMilli(last.endTime).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

            val measurement = AndroidMeasurementModel(
                deviceId = deviceId,
                totalWatts = totalWatts,
                totalWattHours = totalWattHours,
                totalGramsCO2 = gramsCO2,
                from = fromTime,
                to = toTime,
                batteryLevelFrom = first.level,
                batteryLevelTo = last.level,
                isCharging = true,
                averageAmpere = averageAmpere,
                averageVoltage = averageVoltage,
                interval = INTERVAL_SEC,
                totalSamples = readings.size,
                sourceType = last.source,
                batteryCapacity = batteryCapacityMah,
                emissionFactor = emissionFactor,
                cfeScore = stationInfo?.cfeScore,
                wifiAddress = wifiBssid,
                stationCode = stationCode,
                stationId = stationInfo?.stationId,
                isGreen = stationInfo?.isGreen,
                latitude = latitude,
                longitude = longitude
            )

            Log.d(TAG, "Uploading aggregated batch: ${readings.size} readings, ${String.format("%.3f", totalWattHours)}Wh")

            val hasValidContext = wifiBssid != null && (latitude != 0.0 || longitude != 0.0)
            if (hasValidContext) {
                val api = NetworkModule.provideThingsApiService(context)
                val response = api.addDeviceConsumptionRange(listOf(measurement))
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully uploaded aggregated batch")
                    readingDao.deleteByGroup(groupId)
                    lastBatchUploadTime = System.currentTimeMillis()
                    syncPendingConsumptions()
                } else {
                    Log.w(TAG, "Upload failed: ${response.code()}, saving to local DB")
                    saveMeasurementToPending(measurement)
                    readingDao.deleteByGroup(groupId)
                    lastBatchUploadTime = System.currentTimeMillis()
                }
            } else {
                Log.d(TAG, "No WiFi/location, saving aggregated measurement to local DB")
                saveMeasurementToPending(measurement)
                readingDao.deleteByGroup(groupId)
                lastBatchUploadTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in aggregateAndUpload", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "aggregateAndUpload")
                Sentry.captureException(e)
            }
        }
    }

    private suspend fun saveMeasurementToPending(measurement: AndroidMeasurementModel) {
        try {
            val json = gson.toJson(measurement)
            val entity = ConsumptionEntity(modelJson = json)
            database.consumptionDao().insert(entity)
            Log.d(TAG, "Saved aggregated measurement to pending_consumption")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to pending", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "saveMeasurementToPending")
                Sentry.captureException(e)
            }
        }
    }

    suspend fun syncPendingConsumptions() {
        try {
            val pendingList = database.consumptionDao().getAllPendingOnce()
            if (pendingList.isEmpty()) return
            Log.d(TAG, "Syncing ${pendingList.size} pending consumptions...")
            val measurements = pendingList.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.modelJson, AndroidMeasurementModel::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing consumption entity: ${e.message}")
                    null
                }
            }
            if (measurements.isEmpty()) return
            val api = NetworkModule.provideThingsApiService(context)
            val response = api.addDeviceConsumptionRange(measurements)
            if (response.isSuccessful) {
                val ids = pendingList.map { it.id }
                database.consumptionDao().deleteByIds(ids)
                Log.d(TAG, "Synced and deleted ${ids.size} pending consumptions")
            } else {
                Log.w(TAG, "Batch sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing pending consumptions", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "syncPendingConsumptions")
                Sentry.captureException(e)
            }
        }
    }

    private fun resetTracking() {
        currentGroupId = null
    }

    fun getIntervalMs(): Long = CONSUMPTION_INTERVAL_MS

    fun getBatchUploadIntervalMs(): Long = BATCH_UPLOAD_INTERVAL_MS

    suspend fun getBatchSize(): Int = currentGroupId?.let { readingDao.getCountForGroup(it) } ?: 0

    suspend fun forceUploadBatch() {
        Log.d(TAG, "Force uploading batch...")
        aggregateAndUpload()
    }
}
