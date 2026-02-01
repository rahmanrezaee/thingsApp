package com.example.thingsappandroid.services.utils

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.thingsappandroid.data.local.AppDatabase
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity
import com.example.thingsappandroid.data.model.AndroidMeasurementModel
import com.example.thingsappandroid.data.model.StationInfo
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import com.google.gson.Gson
import io.sentry.Sentry
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Handles consumption tracking during charging sessions.
 * Records measurements every 5 seconds and stores offline when needed.
 */
class ConsumptionTracker(
    private val context: Context,
    private val deviceId: String,
    private val batteryManager: BatteryManager
) {
    private val TAG = "ConsumptionTracker"
    private val database = AppDatabase.getInstance(context)
    private val gson = Gson()
    
    private var consumptionStartTime: Long = 0
    private var consumptionStartLevel: Int = 0
    private var consumptionStartVoltage: Int = 0
    
    companion object {
        const val CONSUMPTION_INTERVAL_MS = 5 * 1000L // 5 seconds
        private const val MIN_DURATION_MS = 1_000L // 1 second
    }
    
    /**
     * Start tracking consumption when charging begins
     */
    fun startTracking(level: Int, voltage: Int) {
        consumptionStartTime = System.currentTimeMillis()
        consumptionStartLevel = level
        consumptionStartVoltage = voltage
        Log.d(TAG, "Started consumption tracking - Level: $level%, Voltage: ${voltage}mV")
    }
    
    /**
     * Stop tracking and record final consumption when charging stops
     */
    suspend fun stopTracking(level: Int, voltage: Int) {
        if (consumptionStartTime > 0) {
            recordConsumption(level, voltage, isCharging = true, isFinal = true)
            resetTracking()
            Log.d(TAG, "Stopped consumption tracking")
        }
    }
    
    /**
     * Record a consumption measurement
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun recordConsumption(currentLevel: Int, currentVoltage: Int, isCharging: Boolean, isFinal: Boolean = false) {
        try {
            val now = System.currentTimeMillis()
            val durationMs = now - consumptionStartTime
            
            // Skip if duration is too short (less than 1 second)
            if (durationMs < MIN_DURATION_MS && !isFinal) {
                Log.d(TAG, "Skipping consumption record - duration too short: ${durationMs}ms")
                return
            }
            
            val durationHours = durationMs / (1000.0 * 60.0 * 60.0)
            val levelDelta = if (isCharging) {
                currentLevel - consumptionStartLevel
            } else {
                consumptionStartLevel - currentLevel
            }
            
            val avgVoltage = (consumptionStartVoltage + currentVoltage) / 2.0
            
            // Get battery capacity
            val batteryCapacityMwh = try {
                val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (chargeCounter > 0) chargeCounter / 1000 else 3000 // Default 3000mAh
            } catch (e: Exception) {
                3000 // Default 3000mAh
            }
            
            // Calculate consumption
            // If levelDelta is 0, avgAmpere will be 0, which is correct for 5s intervals
            val avgAmpere = if (durationHours > 0) (batteryCapacityMwh * (levelDelta / 100.0)) / durationHours else 0.0
            val watts = (avgVoltage / 1000.0) * avgAmpere
            val wattHours = watts * durationHours
            
            // Get location
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            
            // Get WiFi BSSID
            val wifiBssid = WifiUtils.getHashedWiFiBSSID(context)
            
            // Get station info
            val preferenceManager = PreferenceManager(context)
            val stationCode = preferenceManager.getStationCode()
            val stationInfo = preferenceManager.getStationInfo()
            
            // Get emission factor (grid intensity)
            val emissionFactor = preferenceManager.getCarbonIntensity() ?: 485
            
            // Calculate CO2
            val gramsCO2 = (wattHours / 1000.0) * emissionFactor
            
            // Create measurement model
            val fromTime = Instant.ofEpochMilli(consumptionStartTime)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)
            val toTime = Instant.ofEpochMilli(now)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)
            
            val measurement = AndroidMeasurementModel(
                deviceId = deviceId,
                totalWatts = watts,
                totalWattHours = wattHours,
                totalGramsCO2 = gramsCO2,
                from = fromTime,
                to = toTime,
                batteryLevelFrom = consumptionStartLevel,
                batteryLevelTo = currentLevel,
                isCharging = isCharging,
                averageAmpere = avgAmpere,
                averageVoltage = avgVoltage / 1000.0,
                interval = (durationMs / 1000).toInt(),
                totalSamples = 1,
                sourceType = if (isCharging) "android_charging" else "android_battery",
                batteryCapacity = batteryCapacityMwh,
                emissionFactor = emissionFactor,
                cfeScore = stationInfo?.cfeScore,
                wifiAddress = wifiBssid,
                stationCode = stationCode,
                stationId = stationInfo?.stationId,
                isGreen = stationInfo?.isGreen,
                latitude = latitude,
                longitude = longitude
            )
            
            Log.d(TAG, "Recording consumption (${if(isCharging) "Charging" else "Battery"}): ${wattHours}Wh, Level: $consumptionStartLevel% -> $currentLevel%")
            
            // Restart interval for continuous tracking
            consumptionStartTime = now
            consumptionStartLevel = currentLevel
            consumptionStartVoltage = currentVoltage

            // Check if we have WiFi and location
            val hasWifi = wifiBssid != null
            val hasLocation = latitude != 0.0 || longitude != 0.0
            
            if (hasWifi && hasLocation) {
                uploadConsumption(measurement)
            } else {
                saveToLocalDb(measurement)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording consumption", e)
        }
    }
    
    /**
     * Upload consumption to server
     */
    private suspend fun uploadConsumption(measurement: AndroidMeasurementModel) {
        try {
            val api = NetworkModule.provideThingsApiService(context)
            val response = api.addDeviceConsumption(measurement)
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Consumption uploaded successfully")
                // Try to sync any pending consumptions
                syncPendingConsumptions()
            } else {
                Log.w(TAG, "⚠️ Consumption upload failed: ${response.code()}, saving to local DB")
                saveToLocalDb(measurement)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error uploading consumption: ${e.message}, saving to local DB")
            saveToLocalDb(measurement)
        }
    }
    
    /**
     * Save consumption to local database
     */
    private suspend fun saveToLocalDb(measurement: AndroidMeasurementModel) {
        try {
            val json = gson.toJson(measurement)
            val entity = ConsumptionEntity(modelJson = json)
            database.consumptionDao().insert(entity)
            Log.d(TAG, "💾 Consumption saved to local DB")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving consumption to local DB", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "saveConsumptionToLocalDb")
                Sentry.captureException(e)
            }
        }
    }
    
    /**
     * Sync pending consumptions from local DB to server
     */
    suspend fun syncPendingConsumptions() {
        try {
            val pendingList = database.consumptionDao().getAllPendingOnce()
            
            if (pendingList.isEmpty()) {
                Log.d(TAG, "No pending consumptions to sync")
                return
            }
            
            Log.d(TAG, "📤 Syncing ${pendingList.size} pending consumptions...")
            
            val measurements = pendingList.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.modelJson, AndroidMeasurementModel::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing consumption entity: ${e.message}")
                    null
                }
            }
            
            if (measurements.isEmpty()) {
                Log.w(TAG, "No valid measurements to sync")
                return
            }
            
            // Upload in batch
            val api = NetworkModule.provideThingsApiService(context)
            val response = api.addDeviceConsumptionRange(measurements)
            
            if (response.isSuccessful) {
                // Delete synced records
                val ids = pendingList.map { it.id }
                database.consumptionDao().deleteByIds(ids)
                Log.d(TAG, "✅ Successfully synced and deleted ${ids.size} pending consumptions")
            } else {
                Log.w(TAG, "⚠️ Batch sync failed: ${response.code()}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing pending consumptions", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "syncPendingConsumptions")
                Sentry.captureException(e)
            }
        }
    }
    
    /**
     * Reset tracking state
     */
    private fun resetTracking() {
        consumptionStartTime = 0
        consumptionStartLevel = 0
        consumptionStartVoltage = 0
    }
    
    /**
     * Get interval duration in milliseconds
     */
    fun getIntervalMs(): Long = CONSUMPTION_INTERVAL_MS
}
