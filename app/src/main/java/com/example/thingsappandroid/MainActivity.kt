package com.example.thingsappandroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.example.thingsappandroid.data.local.PreferenceManager
// import com.example.thingsappandroid.features.profile.viewModel.ThemeViewModel
// import com.example.thingsappandroid.ui.theme.ThemeMode
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.navigation.AppNavigation
import com.example.thingsappandroid.services.BatteryService
import com.example.thingsappandroid.ui.components.GlobalMessageHost
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import io.sentry.Sentry

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    // Both location and notification are required; app is blocked until granted.
    private var hasAllRequiredPermissions by mutableStateOf(false)

    private fun computeHasAllRequiredPermissions(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return locationGranted && notificationGranted
    }

    // Permission request launcher for location and notifications
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        } else {
            true
        }

        val locationGranted = fineLocationGranted || coarseLocationGranted
        hasAllRequiredPermissions = locationGranted && notificationGranted

        if (hasAllRequiredPermissions) {
            Toast.makeText(this, "Permissions granted. You can use the app.", Toast.LENGTH_SHORT).show()
            startChargingDetectionService()
        } else {
            if (!locationGranted) {
                Toast.makeText(
                    this,
                    "Location permission is required to use this app.",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                Toast.makeText(
                    this,
                    "Notification permission is required to use this app.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Request location and notification permissions. Call when user taps "Grant permissions" on the required-permissions screen. */
    fun checkAndRequestPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val permissionsToRequest = mutableListOf<String>()
        if (!fineLocationGranted) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!coarseLocationGranted) permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startChargingDetectionService() {
        try {
            // For Android 14+ (API 34), check if notification permission is granted before starting foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val notificationGranted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!notificationGranted) {
                    Log.w("MainActivity", "Notification permission not granted, delaying service start")
                    Toast.makeText(
                        this,
                        "ERROR: Notification permission required for Android 14+ (${Build.MANUFACTURER} ${Build.MODEL})",
                        Toast.LENGTH_LONG
                    ).show()
                    // Service will be started after permission is granted
                    return
                }
            }
            
            val serviceIntent = Intent(this, BatteryService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d("MainActivity", "Starting foreground service BatteryService")
                    startForegroundService(serviceIntent)
                    Log.d("MainActivity", "startForegroundService() called successfully")
                } else {
                    Log.d("MainActivity", "Starting background service BatteryService")
                    startService(serviceIntent)
                    Log.d("MainActivity", "startService() called successfully")
                }
                Log.d("MainActivity", "Service start command issued for BatteryService")
            } catch (e: Exception) {
                Log.e("MainActivity", "ERROR starting BatteryService: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(this, "Failed to start BatteryService: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // Check if service is running after a short delay
            // Use a Handler that's properly scoped to avoid dead thread issues
            val handler = android.os.Handler(mainLooper)
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    if (isServiceRunning(BatteryService::class.java)) {
                        Log.d("MainActivity", "Service is confirmed running")
                        Toast.makeText(this, "Background service started", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.w("MainActivity", "Service may not be running")
                        Toast.makeText(this, "Service start failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 2000)
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException starting service", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "startChargingDetectionService")
                scope.setTag("error_type", "SecurityException")
                scope.setContexts("service", mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdk_int" to Build.VERSION.SDK_INT.toString()
                ))
                Sentry.captureException(e)
            }
            Toast.makeText(
                this,
                "ERROR: Security - Cannot start service: ${e.message} (${Build.MANUFACTURER} ${Build.MODEL} API${Build.VERSION.SDK_INT})",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: IllegalStateException) {
            Log.e("MainActivity", "IllegalStateException starting service", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "startChargingDetectionService")
                scope.setTag("error_type", "IllegalStateException")
                scope.setContexts("service", mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdk_int" to Build.VERSION.SDK_INT.toString()
                ))
                Sentry.captureException(e)
            }
            Toast.makeText(
                this,
                "ERROR: IllegalState - Service start failed: ${e.message} (${Build.MANUFACTURER} ${Build.MODEL} API${Build.VERSION.SDK_INT})",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "startChargingDetectionService")
                scope.setContexts("service", mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdk_int" to Build.VERSION.SDK_INT.toString()
                ))
                Sentry.captureException(e)
            }
            Toast.makeText(
                this,
                "ERROR: ${e.javaClass.simpleName} - Service start failed: ${e.message} (${Build.MANUFACTURER} ${Build.MODEL} API${Build.VERSION.SDK_INT})",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAllRequiredPermissions = computeHasAllRequiredPermissions()

        // Start service only if both permissions are already granted (e.g. returning user)
        if (hasAllRequiredPermissions) {
            startChargingDetectionService()
        }

        handleDeepLink(intent)
        checkStationCodeIntent(intent)

        setContent {
            ThingsAppAndroidTheme {
                val navController = rememberNavController()
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        navController = navController,
                        onOnboardingFinished = { preferenceManager.setOnboardingCompleted(true) },
                        onRequestPermissions = { checkAndRequestPermissions() },
                        hasAllRequiredPermissions = hasAllRequiredPermissions,
                        initialIntent = intent
                    )

                    GlobalMessageHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        intent?.let {
            setIntent(it) // Update the activity intent so checkStationCodeIntent sees latest extras
            handleDeepLink(it)
            checkStationCodeIntent(it)
        }
    }

    private fun checkStationCodeIntent(intent: Intent?) {
        Log.d(
            "MainActivity",
            "Checking intent: Action=${intent?.action}, Extras=${intent?.extras?.keySet()}"
        )

        if (intent?.action == "com.example.thingsappandroid.OPEN_STATION_CODE" ||
            intent?.getBooleanExtra("open_station_code_dialog", false) == true
        ) {

            Log.d("MainActivity", "STATION CODE INTENT DETECTED. Dispatching to ViewModel.")
            try {
                val viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
                viewModel.dispatch(ActivityIntent.OpenStationCodeDialog)
                Log.d("MainActivity", "Dispatched OpenStationCodeDialog to ActivityViewModel")
                intent.action = null
                intent.removeExtra("open_station_code_dialog")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error dispatching station code intent", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "checkStationCodeIntent")
                    Sentry.captureException(e)
                }
            }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.data != null) {
            val data = intent.data
            Log.d(
                "MainActivity",
                "Deep link received: ${data?.scheme}://${data?.host}${data?.path}?${data?.query}"
            )
        }
    }
}