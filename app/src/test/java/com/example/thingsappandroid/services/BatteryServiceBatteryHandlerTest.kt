package com.example.thingsappandroid.services

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [BatteryServiceBatteryHandler] logic.
 * Aligns with ThingsApp Flow: battery state parsing for charging/discharging transitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BatteryServiceBatteryHandlerTest {

    private fun createBatteryIntent(
        level: Int = 80,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_CHARGING,
        voltage: Int = 4200,
        plugged: Int = BatteryManager.BATTERY_PLUGGED_AC,
        temperature: Int = 250,
        health: Int = BatteryManager.BATTERY_HEALTH_GOOD,
        technology: String? = "Li-ion"
    ): Intent {
        return Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, level)
            putExtra(BatteryManager.EXTRA_SCALE, scale)
            putExtra(BatteryManager.EXTRA_STATUS, status)
            putExtra(BatteryManager.EXTRA_VOLTAGE, voltage)
            putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, temperature)
            putExtra(BatteryManager.EXTRA_HEALTH, health)
            technology?.let { putExtra(BatteryManager.EXTRA_TECHNOLOGY, it) }
        }
    }

    @Test
    fun parseBatteryIntent_chargingStatus_parsesCorrectly() {
        val intent = createBatteryIntent(
            level = 75,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertEquals(75, result!!.state.level)
        assertTrue(result.state.isCharging)
        assertEquals(4200, result.state.voltage)
        assertTrue(result.isInitialization)
        assertFalse(result.wasCharging)
    }

    @Test
    fun parseBatteryIntent_fullStatus_treatedAsCharging() {
        val intent = createBatteryIntent(status = BatteryManager.BATTERY_STATUS_FULL)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertTrue(result!!.state.isCharging)
    }

    @Test
    fun parseBatteryIntent_dischargingStatus_notCharging() {
        val intent = createBatteryIntent(status = BatteryManager.BATTERY_STATUS_DISCHARGING)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertFalse(result!!.state.isCharging)
    }

    @Test
    fun parseBatteryIntent_customScale_calculatesPercentageCorrectly() {
        val intent = createBatteryIntent(level = 5, scale = 10) // 50%
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertEquals(50, result!!.state.level)
    }

    @Test
    fun parseBatteryIntent_withPreviousState_wasChargingReflectsPrevious() {
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
        assertFalse(result.isInitialization)
    }

    @Test
    fun parseBatteryIntent_withoutPreviousState_isInitializationTrue() {
        val intent = createBatteryIntent()
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertTrue(result!!.isInitialization)
    }

    @Test
    fun parseBatteryIntent_allFields_populatesBatteryState() {
        val intent = createBatteryIntent(
            level = 90,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            voltage = 4300,
            plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS,
            temperature = 300,
            health = BatteryManager.BATTERY_HEALTH_GOOD,
            technology = "LiPo"
        )
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertEquals(90, result!!.state.level)
        assertEquals(100, result.state.scale)
        assertEquals(4300, result.state.voltage)
        assertEquals(BatteryManager.BATTERY_PLUGGED_WIRELESS, result.state.plugged)
        assertEquals(300, result.state.temperature)
        assertEquals(BatteryManager.BATTERY_HEALTH_GOOD, result.state.health)
        assertEquals("LiPo", result.state.technology)
    }

    @Test
    fun parseBatteryIntent_invalidLevel_returnsZeroPercent() {
        val intent = createBatteryIntent(level = -1, scale = 100)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertEquals(0, result!!.state.level)
    }

    @Test
    fun parseBatteryIntent_zeroScale_returnsZeroPercent() {
        val intent = createBatteryIntent(level = 50, scale = 0)
        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, null)

        assertNotNull(result)
        assertEquals(0, result!!.state.level)
    }
}
