package com.example.thingsappandroid.services

import android.content.Context
import android.os.BatteryManager
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.utils.BatteryConsumptionTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Factory for creating ConsumptionTracker with runtime deviceId.
 * Provided by DI; used by BatteryService.
 */
interface ConsumptionTrackerFactory {
    fun create(
        deviceId: String,
        batteryManager: BatteryManager,
        scope: CoroutineScope,
        getChargeState: () -> BatteryState?,
        getFreshBatteryState: () -> BatteryState?
    ): BatteryConsumptionTracker
}

/**
 * Concrete implementation for Hilt (avoids kapt resolution issues with fun interface).
 */
class ConsumptionTrackerFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ConsumptionTrackerFactory {
    override fun create(
        deviceId: String,
        batteryManager: BatteryManager,
        scope: CoroutineScope,
        getChargeState: () -> BatteryState?,
        getFreshBatteryState: () -> BatteryState?
    ): BatteryConsumptionTracker =
        BatteryConsumptionTracker(context, deviceId, batteryManager, scope, getChargeState, getFreshBatteryState)
}

/**
 * Factory for creating BatteryServiceDeviceInfoApi with runtime deviceId and onUpdated callback.
 * Provided by DI; used by BatteryService.
 */
interface DeviceInfoApiFactory {
    fun create(deviceId: String, onUpdated: () -> Unit, isCharging: () -> Boolean): BatteryServiceDeviceInfoApi
}

/**
 * Concrete implementation for Hilt (avoids kapt resolution issues with fun interface).
 */
class DeviceInfoApiFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val tokenManager: TokenManager,
    private val thingsRepository: ThingsRepository
) : DeviceInfoApiFactory {
    override fun create(deviceId: String, onUpdated: () -> Unit, isCharging: () -> Boolean): BatteryServiceDeviceInfoApi =
        BatteryServiceDeviceInfoApi(context, deviceId, preferenceManager, tokenManager, thingsRepository, onUpdated, isCharging)
}
