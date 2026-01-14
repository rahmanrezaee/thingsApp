package com.example.thingsappandroid.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors battery level and charging status.
 * This is a regular class, not a background Service.
 */
class BatteryMonitor(private val context: Context) {
    private val TAG = "BatteryMonitor"
    
    private val _batteryLevel = MutableStateFlow(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _consumption = MutableStateFlow(0f) // in kWh
    val consumption: StateFlow<Float> = _consumption.asStateFlow()

    private val _voltage = MutableStateFlow(0f) // in volts
    val voltage: StateFlow<Float> = _voltage.asStateFlow()

    private val _watt = MutableStateFlow(0f) // in watts
    val watt: StateFlow<Float> = _watt.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    updateBatteryInfo(intent)
                }
            }
        }
    }

    private fun updateBatteryInfo(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            level * 100f / scale
        } else {
            0f
        }
        _batteryLevel.value = batteryPct / 100f // Normalize to 0-1

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        _isCharging.value = isCharging

        // Get voltage (in millivolts, convert to volts)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)


        if (voltageMv > 0) {
            _voltage.value = voltageMv / 1000f
        }
        
        // Calculate power consumption
        // For Android, we can estimate consumption based on battery level changes
        // This is a simplified calculation - actual consumption monitoring requires more complex logic
        updateConsumption()
    }

    private fun updateConsumption() {
        // Calculate watts: P = V * I
        // For Android devices, we estimate based on voltage and a typical current draw
        // This is a simplified approach - real measurement would require battery current monitoring
        val voltage = _voltage.value
        if (voltage > 0) {
            // Estimate current based on charging status and battery level
            // Typical phone battery: 3.7-4.2V, current varies significantly
            // When charging: higher current (1-2A typical)
            // When discharging: varies by usage (0.1-1A typical)
            val estimatedCurrent = if (_isCharging.value) {
                1.5f // Amperes when charging
            } else {
                0.5f // Amperes when discharging (average)
            }
            
            val watts = voltage * estimatedCurrent
            _watt.value = watts
            
            // Convert to kWh (assuming 1 hour interval for simplicity)
            // In real implementation, you'd track time intervals
            val kwh = watts / 1000f // per hour
            _consumption.value = kwh
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            context.registerReceiver(batteryReceiver, filter)
            Log.d(TAG, "Battery monitoring started")
            
            // Get initial battery state
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                updateBatteryInfo(batteryIntent)
            } else {
                // Fallback to BatteryManager API (requires API 21+)
                try {
                    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (batteryLevel >= 0) {
                        _batteryLevel.value = batteryLevel / 100f
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get battery level from BatteryManager: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting battery monitoring: ${e.message}")
        }
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
            Log.d(TAG, "Battery monitoring stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping battery monitoring: ${e.message}")
        }
    }
}
