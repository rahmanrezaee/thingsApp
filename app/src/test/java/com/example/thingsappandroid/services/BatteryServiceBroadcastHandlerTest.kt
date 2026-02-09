package com.example.thingsappandroid.services

import android.content.Intent
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.net.wifi.WifiManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [BatteryServiceBroadcastHandler] static helpers.
 * Aligns with ThingsApp Flow: intent routing and charging status detection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BatteryServiceBroadcastHandlerTest {

    @Test
    fun isBatteryIntent_actionBatteryChanged_returnsTrue() {
        assertTrue(BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_BATTERY_CHANGED))
    }

    @Test
    fun isBatteryIntent_actionPowerConnected_returnsTrue() {
        assertTrue(BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_POWER_CONNECTED))
    }

    @Test
    fun isBatteryIntent_actionPowerDisconnected_returnsTrue() {
        assertTrue(BatteryServiceBroadcastHandler.isBatteryIntent(Intent.ACTION_POWER_DISCONNECTED))
    }

    @Test
    fun isBatteryIntent_connectivityAction_returnsFalse() {
        assertFalse(BatteryServiceBroadcastHandler.isBatteryIntent(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    @Test
    fun isBatteryIntent_wifiStateChanged_returnsFalse() {
        assertFalse(BatteryServiceBroadcastHandler.isBatteryIntent(WifiManager.WIFI_STATE_CHANGED_ACTION))
    }

    @Test
    fun isBatteryIntent_nullAction_returnsFalse() {
        assertFalse(BatteryServiceBroadcastHandler.isBatteryIntent(null))
    }

    @Test
    fun getChargingStatusFromIntent_batteryIntentCharging_returnsTrue() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        }
        assertTrue(BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent) == true)
    }

    @Test
    fun getChargingStatusFromIntent_batteryIntentFull_returnsTrue() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
        }
        assertTrue(BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent) == true)
    }

    @Test
    fun getChargingStatusFromIntent_batteryIntentDischarging_returnsFalse() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        assertTrue(BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent) == false)
    }

    @Test
    fun getChargingStatusFromIntent_nonBatteryIntent_returnsNull() {
        val intent = Intent(ConnectivityManager.CONNECTIVITY_ACTION)
        assertNull(BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent))
    }

    @Test
    fun getChargingStatusFromIntent_nullIntent_returnsNull() {
        assertNull(BatteryServiceBroadcastHandler.getChargingStatusFromIntent(null))
    }

    @Test
    fun isWifiOrConnectivityAction_wifiStateChanged_returnsTrue() {
        assertTrue(BatteryServiceBroadcastHandler.isWifiOrConnectivityAction(WifiManager.WIFI_STATE_CHANGED_ACTION))
    }

    @Test
    fun isWifiOrConnectivityAction_networkStateChanged_returnsTrue() {
        assertTrue(BatteryServiceBroadcastHandler.isWifiOrConnectivityAction(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }

    @Test
    fun isWifiOrConnectivityAction_connectivityAction_returnsTrue() {
        assertTrue(BatteryServiceBroadcastHandler.isWifiOrConnectivityAction(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    @Test
    fun isWifiOrConnectivityAction_batteryAction_returnsFalse() {
        assertFalse(BatteryServiceBroadcastHandler.isWifiOrConnectivityAction(Intent.ACTION_BATTERY_CHANGED))
    }

    @Test
    fun createIntentFilter_containsExpectedActions() {
        val filter = BatteryServiceBroadcastHandler.createIntentFilter()
        val actions = (0 until filter.countActions()).map { filter.getAction(it) }.toSet()

        assertTrue(actions.contains(BatteryServiceActions.DEVICEINFO_UPDATED))
        assertTrue(actions.contains(BatteryServiceActions.REQUEST_GET_DEVICE_INFO))
        assertTrue(actions.contains(Intent.ACTION_BATTERY_CHANGED))
        assertTrue(actions.contains(Intent.ACTION_POWER_CONNECTED))
        assertTrue(actions.contains(Intent.ACTION_POWER_DISCONNECTED))
        assertTrue(actions.contains(WifiManager.NETWORK_STATE_CHANGED_ACTION))
        assertTrue(actions.contains(WifiManager.WIFI_STATE_CHANGED_ACTION))
        assertTrue(actions.contains(ConnectivityManager.CONNECTIVITY_ACTION))
    }
}
