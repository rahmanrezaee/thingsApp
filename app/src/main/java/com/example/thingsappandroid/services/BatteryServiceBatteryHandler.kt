package com.example.thingsappandroid.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Parses battery intents into BatteryState. Used by BatteryService.
 */
object BatteryServiceBatteryHandler {

    data class BatteryParseResult(
        val state: BatteryState,
        val level: Int,
        val voltage: Int,
        val wasCharging: Boolean,
        val isInitialization: Boolean
    )

    fun parseBatteryIntent(intent: Intent, currentChargeState: BatteryState?): BatteryParseResult? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val wasCharging = currentChargeState?.isCharging ?: false
        val isInitialization = currentChargeState == null
        val state = BatteryState(
            isCharging = isCharging,
            voltage = voltage,
            level = batteryPct,
            plugged = plugged,
            temperature = temperature,
            health = health,
            scale = scale,
            technology = technology,
            statusCode = status
        )
        return BatteryParseResult(state, level, voltage, wasCharging, isInitialization)
    }

    /**
     * Gets current battery state from system (sticky ACTION_BATTERY_CHANGED).
     * Use this when recording consumption so level/voltage are fresh every 10s.
     */
    fun getCurrentBatteryState(context: Context): BatteryState? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        return parseBatteryIntent(intent, null)?.state
    }
}
