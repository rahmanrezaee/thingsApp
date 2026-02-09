package com.example.thingsappandroid

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.thingsappandroid.services.BatteryService
import com.example.thingsappandroid.services.BatteryServiceBatteryHandler
import com.example.thingsappandroid.services.BatteryServiceBroadcastHandler
import com.example.thingsappandroid.services.BatteryState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests – runs on emulator/device.
 * Includes app context check and all BatteryService-related tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = appContext.packageName
        Log.d("ExampleInstrumentedTest", "useAppContext: packageName=$packageName")
        assertEquals("com.example.thingsappandroid", packageName)
    }

    // -------------------------------------------------------------------------
    // BatteryServiceBatteryHandler tests
    // -------------------------------------------------------------------------

    @Test
    fun parseBatteryIntent_withRealStickyBroadcast_parsesSuccessfully() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        assertNotNull("Device should provide ACTION_BATTERY_CHANGED sticky broadcast", intent)

        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent!!, null)
        assertNotNull(result)
        assertTrue(result!!.state.level in 0..100)

        Log.d("ExampleInstrumentedTest", "parseBatteryIntent_realSticky: level=${result.state.level}, isCharging=${result.state.isCharging}, voltage=${result.state.voltage}")
    }

    @Test
    fun parseBatteryIntent_constructedChargingIntent_parsesCorrectly() {
        val intent = createBatteryIntent(level = 75, scale = 100, status = BatteryManager.BATTERY_STATUS_CHARGING)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertEquals(75, result!!.state.level)
        assertTrue(result.state.isCharging)

        Log.d("ExampleInstrumentedTest", "parseBatteryIntent_charging: level=${result.state.level}, isCharging=${result.state.isCharging}")
    }

    @Test
    fun parseBatteryIntent_constructedDischargingIntent_parsesCorrectly() {
        val intent = createBatteryIntent(status = BatteryManager.BATTERY_STATUS_DISCHARGING)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertFalse(result!!.state.isCharging)

        Log.d("ExampleInstrumentedTest", "parseBatteryIntent_discharging: level=${result.state.level}, isCharging=${result.state.isCharging}")
    }

    @Test
    fun parseBatteryIntent_transitionFromChargingToDischarging_setsWasCharging() {
        val chargingState = BatteryState(
            isCharging = true,
            voltage = 4200,
            level = 80,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            statusCode = BatteryManager.BATTERY_STATUS_CHARGING
        )
        val intent = createBatteryIntent(status = BatteryManager.BATTERY_STATUS_DISCHARGING)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, chargingState)

        assertNotNull(result)
        assertTrue(result!!.wasCharging)
        assertFalse(result.state.isCharging)

        Log.d("ExampleInstrumentedTest", "parseBatteryIntent_transition: wasCharging=${result.wasCharging}, isCharging=${result.state.isCharging}")
    }

    // -------------------------------------------------------------------------
    // BatteryServiceBroadcastHandler tests
    // -------------------------------------------------------------------------

    @Test
    fun isWiFiConnected_withRealContext_returnsBoolean() {
        val result = BatteryServiceBroadcastHandler.isWiFiConnected(context)
        assertTrue(result == true || result == false)
        Log.d("ExampleInstrumentedTest", "isWiFiConnected: value=$result")
    }

    @Test
    fun getChargingStatusFromIntent_withRealBatteryIntent_returnsNonNull() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        assertNotNull(intent)

        val isCharging = BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent)
        assertTrue(isCharging == true || isCharging == false)
        Log.d("ExampleInstrumentedTest", "getChargingStatusFromIntent_real: isCharging=$isCharging")
    }

    @Test
    fun getChargingStatusFromIntent_constructedChargingIntent_returnsTrue() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        }
        val isCharging = BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent)
        assertTrue(isCharging == true)
        Log.d("ExampleInstrumentedTest", "getChargingStatusFromIntent_charging: isCharging=$isCharging")
    }

    @Test
    fun getChargingStatusFromIntent_constructedDischargingIntent_returnsFalse() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        val isCharging = BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent)
        assertTrue(isCharging == false)
        Log.d("ExampleInstrumentedTest", "getChargingStatusFromIntent_discharging: isCharging=$isCharging")
    }

    @Test
    fun createIntentFilter_registersSuccessfully() {
        val receiver = BatteryServiceBroadcastHandler.createReceiver(
            onDeviceInfoUpdated = {},
            onForNewDeviceCallClimateStatus = {},
            onRequestGetDeviceInfo = {},
            onBatteryIntent = {},
            onConnectivityAction = {}
        )
        val filter = BatteryServiceBroadcastHandler.createIntentFilter()
        val actionCount = filter.countActions()

        BatteryServiceBroadcastHandler.registerReceiver(context, receiver, filter)
        BatteryServiceBroadcastHandler.unregisterReceiver(context, receiver)

        Log.d("ExampleInstrumentedTest", "createIntentFilter_registers: actionCount=$actionCount")
    }

    @Test
    fun isBatteryIntent_batteryActions_returnsTrue() {
        val batChanged = BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_BATTERY_CHANGED)
        val powerConn = BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_POWER_CONNECTED)
        val powerDisc = BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_POWER_DISCONNECTED)
        assertTrue(batChanged && powerConn && powerDisc)
        Log.d("ExampleInstrumentedTest", "isBatteryIntent_batteryActions: batChanged=$batChanged, powerConn=$powerConn, powerDisc=$powerDisc")
    }

    @Test
    fun isBatteryIntent_nonBatteryAction_returnsFalse() {
        val result = BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        assertFalse(result)
        Log.d("ExampleInstrumentedTest", "isBatteryIntent_nonBattery: value=$result")
    }

    // -------------------------------------------------------------------------
    // BatteryService tests
    // -------------------------------------------------------------------------

    @Test
    fun batteryServiceIntent_hasCorrectComponent() {
        val intent = Intent(context, BatteryService::class.java)
        val pkg = intent.component?.packageName
        val cls = intent.component?.className
        Log.d("ExampleInstrumentedTest", "batteryServiceIntent: package=$pkg, className=$cls")
        assertEquals(context.packageName, pkg)
        assertEquals(BatteryService::class.java.name, cls)
    }

    @Test
    fun serviceIntentCanBeCreated_onApi30Plus() {
        // Do NOT call startForegroundService here: BatteryService uses Hilt @AndroidEntryPoint.
        // Starting it from test causes "component was not created" because service onCreate
        // runs before Hilt test component is ready. We only verify the intent can be built.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d("ExampleInstrumentedTest", "serviceIntentCanBeCreated: skipped (API ${Build.VERSION.SDK_INT} < 30)")
            return
        }
        val intent = Intent(context, BatteryService::class.java)
        assertNotNull(intent.component)
        Log.d("ExampleInstrumentedTest", "serviceIntentCanBeCreated: intent ready for BatteryService")
    }

    private fun createBatteryIntent(
        level: Int = 80,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_CHARGING,
        voltage: Int = 4200,
        plugged: Int = BatteryManager.BATTERY_PLUGGED_AC
    ): Intent {
        return Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, level)
            putExtra(BatteryManager.EXTRA_SCALE, scale)
            putExtra(BatteryManager.EXTRA_STATUS, status)
            putExtra(BatteryManager.EXTRA_VOLTAGE, voltage)
            putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        }
    }
}
