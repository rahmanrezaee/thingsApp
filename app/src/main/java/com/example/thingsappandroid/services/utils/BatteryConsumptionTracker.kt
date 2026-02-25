package com.example.thingsappandroid.services.utils

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.thingsappandroid.data.local.AppDatabase
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity
import com.example.thingsappandroid.data.local.entity.ConsumptionItemEntity
import com.example.thingsappandroid.data.model.AddDeviceConsumptionBody
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
    private val consumptionDao = database.consumptionDao()

    private var currentConsumptionId: Long? = null
    private var loopJob: Job? = null

    companion object {
        const val INTERVAL_MS = 10_000L

        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
    }

    fun startChargingSessionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (loopJob?.isActive == true) return

        val state = getFreshBatteryState() ?: getChargeState()
        if (state?.isCharging != true) return

        scope.launch {
            if (currentConsumptionId == null) {
                val newConsumption = ConsumptionEntity(
                    startLevel = state.level,
                    endLevel = state.level,
                    totalWatt = 0.0,
                    totalWattHours = 0.0,
                    averageAmpere = 0.0,
                    averageVoltage = 0.0,
                    intervalSeconds = (INTERVAL_MS / 1000).toInt(),
                    totalSamples = 0,
                    sourceType = "Charging",
                    startTime = System.currentTimeMillis(),
                    endTime = System.currentTimeMillis(),
                    uploadStatus = STATUS_IN_PROGRESS
                )
                currentConsumptionId = consumptionDao.insertConsumption(newConsumption)
            }

            val cid = currentConsumptionId!!
            Log.d(tag, "startChargingSessionIfNeeded: consumptionId=$cid")

            loopJob = scope.launch {
                var tick = 0
                while (isActive && getChargeState()?.isCharging == true) {
                    delay(INTERVAL_MS)
                    tick++
                    val fresh = getFreshBatteryState()
                    val fallback = getChargeState()
                    val toRecord = fresh ?: fallback
                    if (toRecord != null && toRecord.isCharging) {
                        Log.d(
                            tag,
                            "10s tick #$tick – recording level=${toRecord.level}% V=${toRecord.voltage}mV"
                        )
                        recordReadingInternal(cid, toRecord)
                    } else {
                        Log.d(
                            tag,
                            "10s tick #$tick – skip (state=${toRecord != null}, charging=${toRecord?.isCharging})"
                        )
                    }
                }
                Log.d(tag, "Loop ended (charging ended)")
                stopChargingSessionAndUpload()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun stopChargingSessionAndUpload() {
        loopJob?.cancel()
        loopJob = null

        val cid = currentConsumptionId
        if (cid != null) {
            currentConsumptionId = null
            aggregateAndFinalizeConsumption(cid)

        }
        uploadAllPendingConsumptions()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun recordReadingInternal(consumptionId: Long, state: BatteryState) {
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
            val startTime = now - INTERVAL_MS

            val item = ConsumptionItemEntity(
                consumptionId = consumptionId,
                voltage = state.voltage,
                level = state.level,
                source = state.plugged(),
                ampere = ampere,
                watt = watt,
                startTime = startTime,
                endTime = now
            )
            consumptionDao.insertItem(item)
        } catch (e: Exception) {
            Log.e(tag, "Error recording reading", e)
        }
    }

    private suspend fun aggregateAndFinalizeConsumption(consumptionId: Long) {
        try {
            val items = consumptionDao.getItemsForConsumption(consumptionId)
            if (items.isEmpty()) return

            val first = items.first()
            val last = items.last()

            val avgWatts = items.map { kotlin.math.abs(it.watt) }.average()
            val avgAmpere = items.map { kotlin.math.abs(it.ampere) }.average()
            val avgVoltage = items.map { it.voltage.toDouble() }.average()
            val totalWh = items.sumOf { r ->
                val durationMs = r.endTime - r.startTime
                val durationHours = durationMs / 3600_000.0
                val wattValue = if (r.watt < 0) -r.watt else r.watt
                wattValue * durationHours
            }

            val intervalSeconds = (INTERVAL_MS / 1000).toInt()
            val totalSamples = items.size
            val sourceType = items.firstOrNull()?.source ?: "Charging"

            consumptionDao.updateConsumptionSummary(
                id = consumptionId,
                totalWatt = avgWatts,
                totalWattHours = totalWh,
                averageAmpere = avgAmpere,
                averageVoltage = avgVoltage / 1000,
                intervalSeconds = intervalSeconds,
                totalSamples = totalSamples,
                sourceType = sourceType,
                startTime = first.startTime,
                endTime = last.endTime,
                startLevel = first.level,
                endLevel = last.level,
                status = STATUS_PENDING,
                uploadedAt = null
            )
            Log.d(tag, "Finalized consumption $consumptionId: status=PENDING, Wh=$totalWh")
        } catch (e: Exception) {
            Log.e(tag, "Error finalizing consumption $consumptionId", e)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun uploadAllPendingConsumptions() {
        try {
            val pendingList = consumptionDao.getConsumptionsByStatus(STATUS_PENDING)
            if (pendingList.isEmpty()) {
                Log.d(tag, "No pending consumptions to upload")
                return
            }

            val api = NetworkModule.provideThingsApiService(context)
            val bodies = mutableListOf<Pair<Long, AddDeviceConsumptionBody>>()

            val batteryCapacityMah = BatteryUtil.getBatteryCapacity(context)
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(
                0.0,
                0.0
            )
            val preferenceManager = PreferenceManager(context)
            val wifiBssid = WifiUtils.getHashedWiFiBSSID(context)
            val stationCode = preferenceManager.getStationCode()
            val stationInfo = preferenceManager.getStationInfo()
            val emissionFactor = preferenceManager.getCarbonIntensity() ?: 485
            val climateStatus = preferenceManager.getLastDeviceInfo()?.climateStatus
            val isGreen = stationInfo?.isGreen
            val isVerifiedAsGreen = isGreen ?: false

            for (entity in pendingList) {
                val gramsCO2 = (entity.totalWattHours / 1000.0) * emissionFactor

                val fromTime = Instant.ofEpochMilli(entity.startTime).atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)
                val toTime = Instant.ofEpochMilli(entity.endTime).atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)

                val location =
                    if (latitude != 0.0 || longitude != 0.0) Location(latitude, longitude) else null

                val body = AddDeviceConsumptionBody(
                    deviceId = deviceId,
                    totalWatts = entity.totalWatt,
                    totalWattHours = entity.totalWattHours,
                    totalGramsCO2 = gramsCO2,
                    from = fromTime,
                    to = toTime,
                    batteryLevelFrom = entity.startLevel,
                    batteryLevelTo = entity.endLevel,
                    isCharging = true,
                    sourceType = entity.sourceType ?: "Charging",
                    batteryCapacity = batteryCapacityMah,
                    emissionFactor = emissionFactor,
                    cfeScore = stationInfo?.cfeScore,
                    wifiAddress = wifiBssid,
                    stationCode = stationCode,
                    isGreen = isGreen,
                    climateStatus = climateStatus,
                    location = location,
                    isVerifiedAsGreen = isVerifiedAsGreen,
                    averageAmpere = entity.averageAmpere,
                    averageVoltage = entity.averageVoltage,
                    interval = entity.intervalSeconds,
                    totalSamples = entity.totalSamples
                )
                bodies.add(entity.id to body)
            }

            if (bodies.isEmpty()) return

            val bodyList = bodies.map { it.second }
            val resp = api.addDeviceConsumptionRange(bodyList)

            if (resp.isSuccessful) {
                bodies.forEach { (id, _) ->
                    consumptionDao.updateConsumptionStatus(
                        id,
                        STATUS_UPLOADED,
                        System.currentTimeMillis()
                    )
                }
                Log.d(tag, "Successfully uploaded ${bodies.size} consumption records")
            } else {
                val errorBody = resp.errorBody()?.string()
                Log.e(
                    tag,
                    "Failed to upload consumption range: $errorBody (Status: ${resp.code()})"
                )
                // Keep as PENDING or mark as FAILED if you prefer
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during consumption range upload", e)
            Sentry.captureException(e)
        }
    }
}
