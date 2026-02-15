package com.example.thingsappandroid.services

import android.util.Log
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.thingsappandroid.services.utils.ConsumptionTracker

private const val TAG = "ConsumptionHandler"


/**
 * Consumption tracking: periodic reports and sync. Used by BatteryService.
 * Checks every 10 seconds and uses fresh battery state from the system for correct readings.
 */
object BatteryServiceConsumptionHandler {

    fun startPeriodicConsumptionReports(
        scope: CoroutineScope,
        getChargeState: () -> BatteryState?,
        getCurrentBatteryState: () -> BatteryState?,
        consumptionTracker: ConsumptionTracker
    ) {
        scope.launch {
            var tickCount = 0
            while (getChargeState()?.isCharging == true) {
                delay(consumptionTracker.getIntervalMs())
                tickCount++
                val freshState = getCurrentBatteryState()
                val fallbackState = getChargeState()
                val stateToRecord = freshState ?: fallbackState
                if (stateToRecord != null && stateToRecord.isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "10s tick #$tickCount – recording (level=${stateToRecord.level}%, voltage=${stateToRecord.voltage}mV, source=${stateToRecord.plugged()})")
                    consumptionTracker.recordReading(stateToRecord)
                } else {
                    Log.d(TAG, "10s tick #$tickCount – skip (state=${stateToRecord != null}, charging=${stateToRecord?.isCharging})")
                }
            }
            Log.d(TAG, "Stopped periodic consumption (charging ended after $tickCount ticks)")
        }
    }

    suspend fun syncPendingConsumptions(consumptionTracker: ConsumptionTracker) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            consumptionTracker.syncPendingConsumptions()
        }
    }
}
