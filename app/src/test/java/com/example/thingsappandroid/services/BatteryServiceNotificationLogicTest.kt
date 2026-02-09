package com.example.thingsappandroid.services

import android.app.Service
import android.app.NotificationManager
import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.services.utils.BatteryNotificationHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [BatteryServiceNotificationHandler] logic, especially
 * shouldShowStationCodeNotification - aligns with ThingsApp Flow Spec:
 * "If ClimateStatus result is not 5, 6, 7, or 9 → Display notification prompting user to enter Station Code"
 *
 * Uses SDK 30+ because shouldShowStationCodeNotification requires Build.VERSION_CODES.R.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class BatteryServiceNotificationLogicTest {

    private lateinit var notificationHandler: BatteryServiceNotificationHandler
    private lateinit var preferenceManager: PreferenceManager
    private var stationCodeShownThisSession = false

    @Before
    fun setup() {
        val service = mockk<Service>(relaxed = true)
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        preferenceManager = mockk(relaxed = true)
        val notificationHelper = mockk<BatteryNotificationHelper>(relaxed = true)
        val scope = CoroutineScope(Dispatchers.Unconfined)

        notificationHandler = BatteryServiceNotificationHandler(
            service = service,
            notificationManager = notificationManager,
            preferenceManager = preferenceManager,
            notificationHelper = notificationHelper,
            scope = scope,
            getContentIntent = { mockk(relaxed = true) },
            getCurrentDCS = { DeviceClimateStatus.UNKNOWN },
            getStationCodeShownThisSession = { stationCodeShownThisSession },
            setStationCodeShownThisSession = { stationCodeShownThisSession = it },
            getCurrentStationCode = { null }
        )
    }

    @Test
    fun shouldShowStationCodeNotification_notCharging_returnsFalse() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 4 // Not green
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = false)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_alreadyShownThisSession_returnsFalse() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 4 // Not green
        }
        stationCodeShownThisSession = true

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_hasStation_returnsFalse() {
        every { preferenceManager.getHasStation() } returns true
        every { preferenceManager.getLastDeviceInfo() } returns mockk {
            every { climateStatus } returns 4
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_climateStatus5_green_returnsFalse() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 5 // Green
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_climateStatus6_green_returnsFalse() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 6
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_climateStatus7_green_returnsFalse() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 7
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_climateStatus9_green_returnsFalse() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 9
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowStationCodeNotification_climateStatus4_notGreen_returnsTrue() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk {
            every { climateStatus } returns 4 // Not green - spec: display station code prompt
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertTrue(result)
    }

    @Test
    fun shouldShowStationCodeNotification_climateStatus8_notGreen_returnsTrue() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns 8 // 1.5°C aligned - not in green list
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertTrue(result)
    }

    @Test
    fun shouldShowStationCodeNotification_nullClimateStatus_returnsTrue() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns mockk<DeviceInfoResponse> {
            every { climateStatus } returns null
        }
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertTrue(result)
    }

    @Test
    fun shouldShowStationCodeNotification_nullDeviceInfo_returnsTrue() {
        every { preferenceManager.getHasStation() } returns false
        every { preferenceManager.getLastDeviceInfo() } returns null
        stationCodeShownThisSession = false

        val result = notificationHandler.shouldShowStationCodeNotification(isCharging = true)

        assertTrue(result)
    }
}
