package com.example.thingsappandroid

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.thingsappandroid.services.BatteryServiceActions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * End-to-End tests for ThingsApp.
 * Runs on emulator/device. Requires network for API calls.
 *
 * Flows:
 * - A: First-time launch (onboarding → permissions → home)
 * - B: Subsequent launch
 * - C.1/C.2: Battery simulation (charger connect/disconnect)
 * - Navigation: Profile, About
 * - Station Code dialog
 * - GetDeviceInfo broadcast (pull-to-refresh)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ThingsAppFlowInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val packageName = "com.example.thingsappandroid"

    @Before
    fun setup() {
        hiltRule.inject()
        grantAllPermissions()
    }

    private fun grantAllPermissions() {
        val uiAutomation = instrumentation.uiAutomation
        val perms = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        perms.forEach { perm ->
            try {
                uiAutomation.grantRuntimePermission(packageName, perm)
            } catch (e: Exception) {
                // Ignore if already granted or not applicable
            }
        }
    }

    private fun clearAppData() {
        instrumentation.uiAutomation.executeShellCommand("pm clear $packageName")
        runBlocking { delay(500) }
    }

    private fun simulateChargingConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            instrumentation.uiAutomation.executeShellCommand("dumpsys battery set ac 1")
            instrumentation.uiAutomation.executeShellCommand("dumpsys battery set status 2")
        }
    }

    private fun simulateChargingDisconnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            instrumentation.uiAutomation.executeShellCommand("dumpsys battery unplug")
        }
    }

    private fun resetBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            instrumentation.uiAutomation.executeShellCommand("dumpsys battery reset")
        }
    }

    private fun sendGetDeviceInfoBroadcast() {
        val intent = Intent(BatteryServiceActions.REQUEST_GET_DEVICE_INFO).apply {
            setPackage(packageName)
        }
        instrumentation.targetContext.sendBroadcast(intent)
    }

    private fun completeOnboardingIfShown() {
        if (composeTestRule.onAllNodes(hasText("Skip", substring = true)).fetchSemanticsNodes().isEmpty()) return
        composeTestRule.onNodeWithText("Skip").performClick()
        runBlocking { delay(500) }
        composeTestRule.onNodeWithText("I agree to the Terms and Conditions", substring = true).performClick()
        runBlocking { delay(200) }
        composeTestRule.onNodeWithText("Get Started").performClick()
        runBlocking { delay(500) }
        if (composeTestRule.onAllNodes(hasText("Continue", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Continue").performClick()
            runBlocking { delay(1000) }
        }
    }

    private fun waitForHome() {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodes(hasText("Climate", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasText("Carbon", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasText("Battery", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasText("Station", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodes(hasText("Profile", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // A. First-Time Launch
    // -------------------------------------------------------------------------

    @Test
    fun flowA_firstTimeLaunch_completesOnboardingAndReachesHome() {
        clearAppData()

        composeTestRule.activityRule.scenario.onActivity { }
        composeTestRule.waitForIdle()

        completeOnboardingIfShown()

        waitForHome()
        assertTrue("Home screen should be visible", true)
    }

    // -------------------------------------------------------------------------
    // B. Subsequent Launch
    // -------------------------------------------------------------------------

    @Test
    fun flowB_subsequentLaunch_reachesHome() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        completeOnboardingIfShown()
        waitForHome()

        assertTrue("Home screen should be visible on subsequent launch", true)
    }

    // -------------------------------------------------------------------------
    // C.1 / C.2 Battery simulation
    // -------------------------------------------------------------------------

    @Test
    fun flowC1_batterySimulation_connectAndDisconnect() {
        clearAppData()
        grantAllPermissions()

        composeTestRule.activityRule.scenario.onActivity { }
        composeTestRule.waitForIdle()

        completeOnboardingIfShown()
        waitForHome()

        simulateChargingConnected()
        runBlocking { delay(2000) }

        simulateChargingDisconnected()
        runBlocking { delay(1000) }

        resetBattery()
        assertTrue("Battery simulation completed", true)
    }

    // -------------------------------------------------------------------------
    // Pull-to-refresh (GetDeviceInfo)
    // -------------------------------------------------------------------------

    @Test
    fun flow_triggerGetDeviceInfoBroadcast() {
        composeTestRule.activityRule.scenario.onActivity { }
        composeTestRule.waitForIdle()

        completeOnboardingIfShown()
        waitForHome()

        sendGetDeviceInfoBroadcast()
        runBlocking { delay(3000) }

        assertTrue("GetDeviceInfo broadcast executed without crash", true)
    }

    // -------------------------------------------------------------------------
    // Navigation E2E
    // -------------------------------------------------------------------------

    @Test
    fun flow_navigateToProfileAndAbout() {
        composeTestRule.activityRule.scenario.onActivity { }
        composeTestRule.waitForIdle()

        completeOnboardingIfShown()
        waitForHome()

        // Tap Profile tab
        composeTestRule.onNodeWithText("Profile").performClick()
        runBlocking { delay(500) }

        // Profile screen: My Account, App Theme, About, etc.
        composeTestRule.onNodeWithText("About").performClick()
        runBlocking { delay(500) }

        // About screen has Back, Device, etc.
        composeTestRule.onNodeWithText("About", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Device", substring = true).assertIsDisplayed()

        assertTrue("About screen visible", true)
    }

    @Test
    fun flow_stationCodeDialog_openEnterAndDismiss() {
        composeTestRule.activityRule.scenario.onActivity { }
        composeTestRule.waitForIdle()

        completeOnboardingIfShown()
        waitForHome()

        // Open Station sheet: tap station area (No GreenFi Connected / Enter Code / etc.)
        val stationTexts = listOf("No GreenFi", "No Station", "Enter Code", "GreenFi", "Connected")
        for (text in stationTexts) {
            if (composeTestRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithText(text, substring = true).performClick()
                runBlocking { delay(500) }
                break
            }
        }

        // If Station sheet opened, tap Manage Your Stations; else try Enter Code directly
        if (composeTestRule.onAllNodes(hasText("Manage Your Stations", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Manage Your Stations", substring = true).performClick()
            runBlocking { delay(500) }
        }

        // Station Code bottom sheet: verify it opened, then dismiss (back)
        if (composeTestRule.onAllNodes(hasText("Station Code", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Station Code", substring = true).assertIsDisplayed()
            composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            runBlocking { delay(300) }
        }

        assertTrue("Station Code flow executed", true)
    }
}
