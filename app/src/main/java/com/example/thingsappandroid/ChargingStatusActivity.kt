package com.example.thingsappandroid

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.thingsappandroid.features.charging.ChargingStatusScreen
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import com.example.thingsappandroid.util.LocationUtils
import kotlinx.coroutines.delay

/**
 * Full-screen notification activity that displays Location status when charging stops.
 * This appears to inform the user about location services status.
 * 
 * Features:
 * - Shows over lock screen
 * - Displays Location services status only (WiFi is assumed always on)
 * - Full-screen alert style
 * - Does not appear in recent apps
 * - Auto-dismisses after 30 seconds
 */
class ChargingStatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("ChargingStatusActivity", "=== Charging Status Full-Screen Notification ===")
        
        configureWindowFlags()
        
        // Only check Location status (WiFi is always on)
        val hasLocationPermission = LocationUtils.hasLocationPermission(this)
        val lastLocation = LocationUtils.getLastKnownLocation(this)

        setContent {
            ThingsAppAndroidTheme {
                val showAutoDismiss = remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    delay(30000)
                    showAutoDismiss.value = true
                }
                
                LaunchedEffect(showAutoDismiss.value) {
                    if (showAutoDismiss.value) {
                        Log.d("ChargingStatusActivity", "Auto-dismissing after 30 seconds")
                        finish()
                    }
                }

                BackHandler {
                    Log.d("ChargingStatusActivity", "Back pressed - closing")
                    finish()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    ChargingStatusScreen(
                        hasLocationPermission = hasLocationPermission,
                        lastLocation = lastLocation,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    private fun configureWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ChargingStatusActivity", "Activity destroyed")
    }
}