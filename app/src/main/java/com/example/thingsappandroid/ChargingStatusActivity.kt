package com.example.thingsappandroid

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import kotlinx.coroutines.delay

/**
 * Activity that shows AlertDialog when charging starts with location disabled.
 * 
 * Features:
 * - Shows over lock screen
 * - Simple AlertDialog UI matching HomeScreen
 * - Does not appear in recent apps
 * - Auto-dismisses after 30 seconds
 */
class ChargingStatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val chargingStarted = intent.getBooleanExtra("charging_started", false)
        
        Log.d("ChargingStatusActivity", "=== Charging Status Activity === charging_started=$chargingStarted")
        
        configureWindowFlags()
        
        // Check Location status from intent (passed from service to avoid race condition)
        val isLocationAvailable = intent.getBooleanExtra("location_available", true)
        Log.d("ChargingStatusActivity", "Location available from intent: $isLocationAvailable")

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

                // Show AlertDialog when charging started and location is not available
                if (chargingStarted && !isLocationAvailable) {
                    LocationEnableAlertDialog(
                        onDismiss = { finish() },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            startActivity(intent)
                            finish()
                        }
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

/**
 * AlertDialog-style location enable prompt
 * Matches the HomeScreen location dialog UI
 */
@Composable
private fun LocationEnableAlertDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(
                "Location Services Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Please enable location services to use this app. Location is required for accurate carbon tracking and WiFi identification.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenSettings()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text("Skip")
            }
        }
    )
}
