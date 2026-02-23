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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Merged tracker: starts a 10s loop when charging starts, saves raw readings into Room,
 * and when charging stops it uploads all pending groups then marks them as uploaded.
 */
class BatteryConsumptionTracker(
    private val context: Context,
    private val deviceId: String,
    private val batteryManager: BatteryManager,
    private val scope: CoroutineScope,
    private val getChargeState: () -> BatteryState?,
    private val getFreshBatteryState: () -> BatteryState?
) {
    private val tag = "BatteryConsumptionTracker"

    private val database = AppDatabase.getInstance(context)
    private val readingDao = database.batteryReadingDao()
    private val consumptionDao = database.consumptionDao()
    private val gson = Gson()

    private var currentGroupId: String? = null
    private var loopJob: Job? = null

    companion object {
        const val INTERVAL_MS = 10_000L
        private const val MIN_READINGS_TO_UPLOAD = 1

        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_FAILED = "FAILED"
    }

    fun startChargingSessionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (loopJob?.isActive == true) return

        val state = getFreshBatteryState() ?: getChargeState()
        if (state?.isCharging != true) return

        if (currentGroupId == null) {
            currentGroupId = java.util.UUID.randomUUID().toString()
        }

        val gid = currentGroupId!!
        Log.d(tag, "startChargingSessionIfNeeded: group=${gid.take(8)}…")

        loopJob = scope.launch {
            var tick = 0
            while (isActive && getChargeState()?.isCharging == true) {
                delay(INTERVAL_MS)
                tick++
                val fresh = getFreshBatteryState()
                val fallback = getChargeState()
                val toRecord = fresh ?: fallback
                if (toRecord != null && toRecord.isCharging) {
                    Log.d(tag, "10s tick #$tick – recording level=${toRecord.level}% V=${toRecord.voltage}mV")
                    recordReadingInternal(gid, toRecord)
                } else {
                    Log.d(tag, "10s tick #$tick – skip (state=${toRecord != null}, charging=${toRecord?.isCharging})")
                }
            }
            Log.d(tag, "Loop ended (charging ended)")
        }
    }

    suspend fun stopChargingSessionAndUpload() {
        loopJob?.cancel()
        loopJob = null

        val gid = currentGroupId
        currentGroupId = null

        // Aggregate current and any other pending sessions then upload
        aggregateAllPendingGroupsAndUpload(gid)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun recordReadingInternal(groupId: String, state: BatteryState) {
        try {
            val currentNow = try {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (e: Exception) {
                Log.w(tag, "recordReading: currentNow unavailable - ${e.message}")
                Int.MIN_VALUE
            }

            val ampere = BatteryUtil.getBatteryCurrentNowInAmperes(currentNow)
            val watt = BatteryUtil.getBatteryCurrentNowInWatt(currentNow, state.voltage)
            val now = System.currentTimeMillis()
            // Ensure the reading covers the full interval for Wh calculation
            val startTime = now - INTERVAL_MS
            val endTime = now

            val reading = BatteryReadingEntity(
                groupId = groupId,
                voltage = state.voltage,
                level = state.level,
                isCharging = state.isCharging,
                source = state.plugged(),
                ampere = ampere,
                watt = watt,
                startTime = startTime,
                endTime = endTime,
                temperature = state.temperature.takeIf { it >= 0 },
                health = state.health.takeIf { it >= 0 }
            )
            readingDao.insert(reading)
        } catch (e: Exception) {
            Log.e(tag, "Error recording reading", e)
        }
    }

    private suspend fun aggregateGroupToPendingConsumption(groupId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val readings = readingDao.getByGroup(groupId)
            if (readings.size < MIN_READINGS_TO_UPLOAD) return

            val first = readings.first()
            val last = readings.last()

            val avgWatts = readings.map { kotlin.math.abs(it.watt) }.average()
            val totalWh = readings.sumOf { r ->
                val durationMs = r.endTime - r.startTime
                val durationHours = durationMs / 3600_000.0
                val wattValue = if (r.watt < 0) -r.watt else r.watt
                wattValue * durationHours
            }

            val batteryCapacityMah = BatteryUtil.getBatteryCapacity(context)
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            val preferenceManager = PreferenceManager(context)
            val wifiBssid = WifiUtils.getHashedWiFiBSSID(context)
            val stationCode = preferenceManager.getStationCode()
            val stationInfo = preferenceManager.getStationInfo()
            val emissionFactor = preferenceManager.getCarbonIntensity() ?: 485
            val gramsCO2 = (totalWh / 1000.0) * emissionFactor
            val climateStatus = preferenceManager.getLastDeviceInfo()?.climateStatus
            val isGreen = stationInfo?.isGreen
            val isVerifiedAsGreen = isGreen ?: false

            val fromTime = Instant.ofEpochMilli(first.startTime).atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)
            val toTime = Instant.ofEpochMilli(last.endTime).atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)

            val measurement = AndroidMeasurementModel(
                deviceId = deviceId,
                totalWatts = avgWatts,
                totalWattHours = totalWh,
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

            readingDao.updateStatusForGroup(groupId, STATUS_PENDING, null, null)
            saveConsumptionEntity(measurement, STATUS_PENDING, null, null)
        } catch (e: Exception) {
            Log.e(tag, "aggregateGroupToPendingConsumption group=${groupId.take(8)}… failed", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "aggregateGroupToPendingConsumption")
                Sentry.captureException(e)
            }
        }
    }

    private suspend fun saveConsumptionEntity(
        measurement: AndroidMeasurementModel,
        status: String,
        measurementId: String?,
        error: String?
    ) {
        val json = gson.toJson(measurement)
        val entity = ConsumptionEntity(
            modelJson = json,
            uploadStatus = status,
            uploadedAt = if (status == STATUS_UPLOADED) System.currentTimeMillis() else null,
            lastUploadError = error,
            measurementId = measurementId
        )
        consumptionDao.insert(entity)
    }

    private suspend fun aggregateAllPendingGroupsAndUpload(currentGid: String?) {
        val groupIds = readingDao.getGroupIdsByStatus(listOf(STATUS_PENDING, STATUS_FAILED)).toMutableList()
        if (currentGid != null && !groupIds.contains(currentGid)) {
            groupIds.add(currentGid)
        }
        for (gid in groupIds) {
            aggregateGroupToPendingConsumption(gid)
        }
        uploadAllPendingConsumptions()
    }


    private suspend fun uploadAllPendingConsumptions() {
        val pendingList = consumptionDao.getByStatusOnce(listOf(STATUS_PENDING, STATUS_FAILED))
        if (pendingList.isEmpty()) return

        val api = NetworkModule.provideThingsApiService(context)
        val bodies = mutableListOf<Pair<Long, AddDeviceConsumptionBody>>()
        
        for (entity in pendingList) {
            val measurement = try {
                gson.fromJson(entity.modelJson, AndroidMeasurementModel::class.java)
            } catch (e: Exception) {
                consumptionDao.updateStatus(entity.id, STATUS_FAILED, null, e.message, null)
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
            bodies.add(entity.id to body)
        }
        
        if (bodies.isEmpty()) return

        val bodyList = bodies.map { it.second }
        try {
            val resp = api.addDeviceConsumptionRange(bodyList)
            if (resp.isSuccessful && resp.body()?.success == true) {
                bodies.forEach { (id, _) ->
                    consumptionDao.updateStatus(id, STATUS_UPLOADED, System.currentTimeMillis(), null, null)
                }
                Log.d(tag, "Successfully uploaded ${bodies.size} consumption records")
            } else {
                val errorBody = resp.errorBody()?.string()
                bodies.forEach { (id, _) ->
                    consumptionDao.updateStatus(id, STATUS_FAILED, null, errorBody, null)
                }
                Log.e(tag, "Failed to upload consumption range: $errorBody")
            }
        } catch (e: Exception) {
            bodies.forEach { (id, _) ->
                consumptionDao.updateStatus(id, STATUS_FAILED, null, e.message, null)
            }
            Log.e(tag, "Exception during consumption range upload", e)
        }
    }
}
