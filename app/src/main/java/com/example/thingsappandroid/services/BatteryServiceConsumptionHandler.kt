package com.example.thingsappandroid.services

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.thingsappandroid.services.utils.ConsumptionTracker

/**
 * Consumption tracking: periodic reports and sync. Used by BatteryService.
 */
object BatteryServiceConsumptionHandler {

    fun startPeriodicConsumptionReports(
        scope: CoroutineScope,
        getChargeState: () -> BatteryState?,
        consumptionTracker: ConsumptionTracker
    ) {
        scope.launch {
            while (getChargeState()?.isCharging == true) {
                delay(consumptionTracker.getIntervalMs())
                val currentState = getChargeState()
                if (currentState != null && currentState.isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    consumptionTracker.recordReading(currentState)
                }
            }
        }
    }

    suspend fun syncPendingConsumptions(consumptionTracker: ConsumptionTracker) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            consumptionTracker.syncPendingConsumptions()
        }
    }
}
