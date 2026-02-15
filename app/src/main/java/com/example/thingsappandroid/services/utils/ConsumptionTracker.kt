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
import com.example.thingsappandroid.data.model.AddDeviceConsumptionBody
import com.example.thingsappandroid.data.model.AndroidMeasurementModel
import com.example.thingsappandroid.data.model.Location
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
        /** Minimum readings to send addDeviceConsumption (1 = send even for very short sessions). */
        private const val MIN_READINGS_TO_UPLOAD = 1
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
     * Record one raw reading (called every 10s). Inserts into Room.
     * Uses actual duration (endTime - startTime) for accurate Wh in aggregation.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun recordReading(state: BatteryState) {
        val groupId = currentGroupId ?: return
        try {
            val currentNow = try {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (e: Exception) {
                Log.w(TAG, "recordReading: currentNow unavailable - ${e.message}")
                Int.MIN_VALUE
            }
            val ampere = BatteryUtil.getBatteryCurrentNowInAmperes(currentNow)
            val watt = BatteryUtil.getBatteryCurrentNowInWatt(currentNow, state.voltage)
            val now = System.currentTimeMillis()
            val durationMs = CONSUMPTION_INTERVAL_MS
            val endTime = now + durationMs
            val whThisSlice = watt * (durationMs / 3600_000.0)

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
            val batchSize = readingDao.getCountForGroup(groupId)
            Log.d(TAG, "Recorded reading: currentNow=${currentNow}µA, V=${state.voltage}mV, level=${state.level}%, " +
                "I=${String.format("%.3f", ampere)}A, P=${String.format("%.3f", watt)}W, " +
                "Wh(slice)=${String.format("%.6f", whThisSlice)}, batchSize=$batchSize, group=${groupId.take(8)}…")
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
                Log.d(TAG, "Skipping addDeviceConsumption - only ${readings.size} readings (need >= $MIN_READINGS_TO_UPLOAD)")
                return
            }

            val first = readings.first()
            val last = readings.last()
            // Charging session: use absolute power/energy (some devices report charging current as negative)
            val totalWatts = readings.map { kotlin.math.abs(it.watt) }.average()
            val totalWattHours = readings.sumOf { r ->
                val durationHours = (r.endTime - r.startTime) / 3600_000.0
                kotlin.math.abs(r.watt) * durationHours
            }
            val spanMs = last.endTime - first.startTime
            Log.d(TAG, "aggregateAndUpload: readings=${readings.size}, span=${spanMs}ms, " +
                "avgP=${String.format("%.4f", totalWatts)}W, totalWh=${String.format("%.6f", totalWattHours)}")

            // Total design capacity in mAh (API expects this). Use PowerProfile; do NOT use
            // CHARGE_COUNTER — that is remaining charge in µAh, not total capacity.
            val batteryCapacityMah = BatteryUtil.getBatteryCapacity(context)

            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val preferenceManager = PreferenceManager(context)
            val wifiBssid = WifiUtils.getHashedWiFiBSSID(context)
            val stationCode = preferenceManager.getStationCode()
            val stationInfo = preferenceManager.getStationInfo()
            val emissionFactor = preferenceManager.getCarbonIntensity() ?: 485
            val gramsCO2 = (totalWattHours / 1000.0) * emissionFactor
            val climateStatus = preferenceManager.getLastDeviceInfo()?.climateStatus
            val isGreen = stationInfo?.isGreen
            val isVerifiedAsGreen = isGreen ?: false

            val fromTime = Instant.ofEpochMilli(first.startTime).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
            val toTime = Instant.ofEpochMilli(last.endTime).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

            val location = if (latitude != 0.0 || longitude != 0.0) Location(latitude, longitude) else null
            val body = AddDeviceConsumptionBody(
                deviceId = deviceId,
                totalWatts = totalWatts,
                totalWattHours = totalWattHours,
                totalGramsCO2 = gramsCO2,
                from = fromTime,
                to = toTime,
                batteryLevelFrom = first.level,
                batteryLevelTo = last.level,
                isCharging = true,
                sourceType = "Charging",
                batteryCapacity = batteryCapacityMah,
                emissionFactor = emissionFactor,
                cfeScore = stationInfo?.cfeScore,
                wifiAddress = wifiBssid,
                stationCode = stationCode,
                isGreen = isGreen,
                climateStatus = climateStatus,
                location = location,
                isVerifiedAsGreen = isVerifiedAsGreen
            )

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
                sourceType = "Charging",
                batteryCapacity = batteryCapacityMah,
                emissionFactor = emissionFactor,
                cfeScore = stationInfo?.cfeScore,
                wifiAddress = wifiBssid,
                stationCode = stationCode,
                isGreen = isGreen,
                climateStatus = climateStatus,
                latitude = latitude,
                longitude = longitude,
                isVerifiedAsGreen = isVerifiedAsGreen
            )

            Log.d(TAG, "Uploading aggregated batch: ${readings.size} readings, " +
                "level ${first.level}%→${last.level}%, ${String.format("%.3f", totalWattHours)}Wh")

            // Upload when we have WiFi (wiFiAddress is required by API). Location can be 0,0 if unavailable.
            val hasValidContext = wifiBssid != null
            if (hasValidContext) {
                try {
                    val requestJson = gson.toJson(body)
                    Log.d(TAG, "adddeviceconsumption REQUEST (single): $requestJson")

                    val api = NetworkModule.provideThingsApiService(context)
                    val response = api.addDeviceConsumption(body)
                    if (response.isSuccessful) {
                        val measurementId = response.body()?.measurementId
                        Log.d(TAG, "Successfully uploaded aggregated batch, measurementId=$measurementId")
                        readingDao.deleteByGroup(groupId)
                        lastBatchUploadTime = System.currentTimeMillis()
                        syncPendingConsumptions()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "no body"
                        Log.w(TAG, "Upload failed: code=${response.code()}, body=$errorBody, saving to local DB")
                        saveMeasurementToPending(measurement)
                        readingDao.deleteByGroup(groupId)
                        lastBatchUploadTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    // Network timeout, IO error, parse error, etc. — save to pending so data is synced later
                    Log.e(TAG, "Upload request failed (e.g. timeout), saving to pending: ${e.message}", e)
                    Sentry.withScope { scope ->
                        scope.setTag("operation", "aggregateAndUpload_upload")
                        Sentry.captureException(e)
                    }
                    saveMeasurementToPending(measurement)
                    readingDao.deleteByGroup(groupId)
                    lastBatchUploadTime = System.currentTimeMillis()
                }
            } else {
                Log.d(TAG, "No WiFi (BSSID unavailable), saving aggregated measurement to local DB")
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
            Log.d(TAG, "Syncing ${pendingList.size} pending consumptions (single adddeviceconsumption each)...")
            val api = NetworkModule.provideThingsApiService(context)
            val succeededIds = mutableListOf<Long>()
            for (entity in pendingList) {
                val measurement = try {
                    gson.fromJson(entity.modelJson, AndroidMeasurementModel::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing consumption entity id=${entity.id}: ${e.message}")
                    continue
                }
                val location = if (measurement.latitude != null && measurement.longitude != null &&
                    (measurement.latitude != 0.0 || measurement.longitude != 0.0)
                ) Location(measurement.latitude, measurement.longitude) else null
                val body = AddDeviceConsumptionBody(
                    deviceId = measurement.deviceId,
                    totalWatts = measurement.totalWatts,
                    totalWattHours = measurement.totalWattHours,
                    totalGramsCO2 = measurement.totalGramsCO2,
                    from = measurement.from,
                    to = measurement.to,
                    batteryLevelFrom = measurement.batteryLevelFrom,
                    batteryLevelTo = measurement.batteryLevelTo,
                    isCharging = measurement.isCharging,
                    sourceType = measurement.sourceType,
                    batteryCapacity = measurement.batteryCapacity,
                    emissionFactor = measurement.emissionFactor,
                    cfeScore = measurement.cfeScore,
                    wifiAddress = measurement.wifiAddress,
                    stationCode = measurement.stationCode,
                    isGreen = measurement.isGreen,
                    climateStatus = measurement.climateStatus,
                    location = location,
                    isVerifiedAsGreen = measurement.isVerifiedAsGreen
                )
                val requestJson = gson.toJson(body)
                Log.d(TAG, "adddeviceconsumption REQUEST (pending id=${entity.id}): $requestJson")
                try {
                    val response = api.addDeviceConsumption(body)
                    if (response.isSuccessful) {
                        val measurementId = response.body()?.measurementId
                        Log.d(TAG, "Pending upload ok: id=${entity.id}, measurementId=$measurementId")
                        succeededIds.add(entity.id)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "no body"
                        Log.w(TAG, "Pending upload failed: id=${entity.id}, code=${response.code()}, body=$errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Pending upload request failed: id=${entity.id}, ${e.message}", e)
                }
            }
            if (succeededIds.isNotEmpty()) {
                database.consumptionDao().deleteByIds(succeededIds)
                Log.d(TAG, "Synced and deleted ${succeededIds.size} pending consumptions")
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
