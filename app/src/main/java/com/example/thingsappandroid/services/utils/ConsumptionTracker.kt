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
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import com.google.gson.Gson
import io.sentry.Sentry
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Handles consumption tracking during charging sessions.
 * Records measurements every 10 seconds and batches them for upload every 10 minutes.
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
    
    // Batch collection for 10-minute uploads
    private val measurementBatch = mutableListOf<AndroidMeasurementModel>()
    private val batchMutex = Mutex()
    private var lastBatchUploadTime: Long = System.currentTimeMillis()
    
    companion object {
        const val CONSUMPTION_INTERVAL_MS = 10 * 1000L // 10 seconds - measurement interval
        private const val BATCH_UPLOAD_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes - upload interval
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
     * Also uploads any remaining batch
     */
    suspend fun stopTracking(level: Int, voltage: Int) {
        if (consumptionStartTime > 0) {
            recordConsumption(level, voltage, isCharging = true, isFinal = true)
            // Upload any remaining measurements in batch
            uploadBatch()
            resetTracking()
            Log.d(TAG, "Stopped consumption tracking and uploaded final batch")
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
            
            val avgVoltageMv = (consumptionStartVoltage + currentVoltage) / 2.0
            
            // Battery capacity in mAh: CHARGE_COUNTER is µAh so /1000 = mAh; fallback to BatteryUtil (mAh)
            val batteryCapacityMah = try {
                val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (chargeCounter > 0) chargeCounter / 1000 else BatteryUtil.getBatteryCapacity(context)
            } catch (e: Exception) {
                BatteryUtil.getBatteryCapacity(context)
            }
            
            // Try to get real-time current measurement (in microamps, negative when charging)
            val currentNowMicroAmps = try {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (e: Exception) {
                0
            }
            
            // Calculate average current using real-time measurement OR battery level delta
            val avgAmpereMa = if (currentNowMicroAmps != 0) {
                // Use real-time current (convert µA to mA, make positive for charging)
                kotlin.math.abs(currentNowMicroAmps / 1000.0)
            } else if (durationHours > 0) {
                // Fallback: calculate from level change
                (batteryCapacityMah * (levelDelta / 100.0)) / durationHours
            } else {
                0.0
            }
            
            val avgAmpereA = avgAmpereMa / 1000.0
            // Power: P = V * I (volts * amperes = watts). Voltage in mV -> V, current in mA -> A.
            val watts = (avgVoltageMv / 1000.0) * avgAmpereA
            val wattHours = watts * durationHours
            
            // Get location
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
            
            val preferenceManager = PreferenceManager(context)
            // WiFi BSSID: only available when app can read it (foreground). In background OS masks BSSID — do not cache; record goes to local DB and syncs when app is in foreground.
            val wifiBssid = WifiUtils.getHashedWiFiBSSID(context)
            
            // Get station info
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
                averageAmpere = avgAmpereA,
                averageVoltage = avgVoltageMv / 1000.0,
                interval = (durationMs / 1000).toInt(),
                totalSamples = 1,
                sourceType = if (isCharging) "android_charging" else "android_battery",
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
            
            val measurementMethod = if (currentNowMicroAmps != 0) "real-time current" else "level delta"
            Log.d(TAG, "Recording consumption (${if(isCharging) "Charging" else "Battery"}): ${String.format("%.3f", wattHours)}Wh, Level: $consumptionStartLevel% -> $currentLevel%, Method: $measurementMethod, Current: ${String.format("%.1f", avgAmpereMa)}mA")
            
            // Restart interval for continuous tracking
            consumptionStartTime = now
            consumptionStartLevel = currentLevel
            consumptionStartVoltage = currentVoltage

            // Add to batch for later upload
            addToBatch(measurement)
            
            // Check if it's time to upload the batch (every 10 minutes)
            checkAndUploadBatch()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording consumption", e)
        }
    }
    
    /**
     * Add measurement to batch for later upload
     */
    private suspend fun addToBatch(measurement: AndroidMeasurementModel) {
        batchMutex.withLock {
            measurementBatch.add(measurement)
            Log.d(TAG, "📊 Added to batch. Current batch size: ${measurementBatch.size}")
        }
    }
    
    /**
     * Check if 10 minutes have passed and upload batch if needed
     */
    private suspend fun checkAndUploadBatch() {
        val now = System.currentTimeMillis()
        val timeSinceLastUpload = now - lastBatchUploadTime
        
        if (timeSinceLastUpload >= BATCH_UPLOAD_INTERVAL_MS) {
            uploadBatch()
        }
    }
    
    /**
     * Upload all measurements collected in the last 10 minutes
     */
    suspend fun uploadBatch() {
        batchMutex.withLock {
            if (measurementBatch.isEmpty()) {
                Log.d(TAG, "📭 No measurements in batch to upload")
                return
            }
            
            val batchSize = measurementBatch.size
            Log.d(TAG, "📤 Uploading batch of $batchSize measurements from last 10 minutes...")
            
            try {
                // Check if we have WiFi and location for at least one measurement
                val hasValidMeasurement = measurementBatch.any { 
                    it.wifiAddress != null && (it.latitude != 0.0 || it.longitude != 0.0)
                }
                
                if (hasValidMeasurement) {
                    // Try to upload batch to server
                    val api = NetworkModule.provideThingsApiService(context)
                    val response = api.addDeviceConsumptionRange(measurementBatch)
                    
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Successfully uploaded batch of $batchSize measurements")
                        // Clear the batch after successful upload
                        measurementBatch.clear()
                        lastBatchUploadTime = System.currentTimeMillis()
                        
                        // Try to sync any pending consumptions from local DB
                        syncPendingConsumptions()
                    } else {
                        Log.w(TAG, "⚠️ Batch upload failed: ${response.code()}, saving to local DB")
                        saveBatchToLocalDb()
                    }
                } else {
                    // No WiFi/location, save to local DB
                    Log.d(TAG, "💾 No WiFi/location available, saving batch to local DB")
                    saveBatchToLocalDb()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error uploading batch: ${e.message}, saving to local DB")
                saveBatchToLocalDb()
            }
        }
    }
    
    /**
     * Save entire batch to local database
     */
    private suspend fun saveBatchToLocalDb() {
        try {
            measurementBatch.forEach { measurement ->
                val json = gson.toJson(measurement)
                val entity = ConsumptionEntity(modelJson = json)
                database.consumptionDao().insert(entity)
            }
            Log.d(TAG, "💾 Batch of ${measurementBatch.size} measurements saved to local DB")
            measurementBatch.clear()
            lastBatchUploadTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving batch to local DB", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "saveBatchToLocalDb")
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
     * Get measurement interval duration in milliseconds
     */
    fun getIntervalMs(): Long = CONSUMPTION_INTERVAL_MS
    
    /**
     * Get batch upload interval duration in milliseconds
     */
    fun getBatchUploadIntervalMs(): Long = BATCH_UPLOAD_INTERVAL_MS
    
    /**
     * Get current batch size for monitoring
     */
    suspend fun getBatchSize(): Int {
        return batchMutex.withLock {
            measurementBatch.size
        }
    }
    
    /**
     * Force upload batch immediately (useful for testing or manual sync)
     */
    suspend fun forceUploadBatch() {
        Log.d(TAG, "🔄 Force uploading batch...")
        uploadBatch()
    }
}
