package com.example.thingsappandroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsappandroid.features.profile.viewModel.ThemeViewModel
import com.example.thingsappandroid.ui.theme.ThemeMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.navigation.AppNavigation
import com.example.thingsappandroid.services.BatteryService
import com.example.thingsappandroid.services.BatteryServiceActions
import com.example.thingsappandroid.ui.components.GlobalMessageHost
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import com.example.thingsappandroid.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import io.sentry.Sentry

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    // Location and Notification granted - can proceed past permission screen.
    private var hasRequiredPermissions by mutableStateOf(false)

    // Last known permission state from previous onResume (used to detect transition when user returns from Settings).
    private var lastKnownBackgroundLocation: Boolean = false

    /**
     * Called from composable when user grants permissions (e.g. from SplashScreen).
     * All permission requests are handled in SplashScreen/AppNavigation.
     */
    fun onPermissionsGranted() {
        hasRequiredPermissions = true
        Log.d("MainActivity", "Permissions granted, starting service")
        startChargingDetectionService()
    }

    /**
     * Handles the REQUEST_LOCATION_PERMISSION intent sent by BatteryService.
     * This is a legacy handler - permission requests are now handled in SplashScreen.
     * Returns true if the intent was consumed, false otherwise.
     */
    private fun handleLocationPermissionRequest(intent: Intent?): Boolean {
        if (intent?.action != "com.example.thingsappandroid.REQUEST_LOCATION_PERMISSION") {
            return false
        }

        Log.d("MainActivity", "REQUEST_LOCATION_PERMISSION intent received from BatteryService")

        if (PermissionUtils.hasForegroundLocationPermission(this)) {
            Log.d("MainActivity", "Location already granted, no action needed")
            return true
        }

        Toast.makeText(
            this,
            "Location permission is required. Please grant it in the app.",
            Toast.LENGTH_LONG
        ).show()

        intent.action = null

        return true
    }

    /**
     * Starts BatteryService only if not already running and all permissions are granted.
     * Requires both foreground and background location permissions.
     */
    fun startChargingDetectionService() {
        try {
            if (isServiceRunning(BatteryService::class.java)) {
                Log.d("MainActivity", "BatteryService already running, skipping start")
                return
            }

            if (!PermissionUtils.hasBackgroundLocationPermission(this)) {
                Log.w("MainActivity", "Background location not granted, cannot start service")
                return
            }

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

        setTheme(R.style.Theme_ThingsAppAndroid)
        super.onCreate(savedInstanceState)
        hasRequiredPermissions = PermissionUtils.hasRequiredPermissions(this)

        if (hasRequiredPermissions && PermissionUtils.hasBackgroundLocationPermission(this)) {
            Log.d("MainActivity", "All permissions granted, starting service")
            startChargingDetectionService()
        } else {
            Log.d("MainActivity", "Waiting for permissions (hasRequired: $hasRequiredPermissions, hasBackground: ${PermissionUtils.hasBackgroundLocationPermission(this)})")
        }

        handleLocationPermissionRequest(intent)
        handleDeepLink(intent)
        checkStationCodeIntent(intent)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
            val darkTheme = themeMode == ThemeMode.Dark ||
                    (themeMode == ThemeMode.System && isSystemInDarkTheme())

            // Sync status bar appearance with current theme
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            ThingsAppAndroidTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                    GlobalMessageHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.setAppInForeground(true)
        val previousPermissions = hasRequiredPermissions
        val previousBackgroundLocation = lastKnownBackgroundLocation
        hasRequiredPermissions = PermissionUtils.hasRequiredPermissions(this)
        val currentBackgroundLocation = PermissionUtils.hasBackgroundLocationPermission(this)
        lastKnownBackgroundLocation = currentBackgroundLocation

        Log.d("MainActivity", "onResume - Required permissions: $hasRequiredPermissions")
        Log.d("MainActivity", "onResume - Background location: $currentBackgroundLocation")

        if (hasRequiredPermissions && currentBackgroundLocation && (!previousPermissions || !previousBackgroundLocation)) {
            Log.d("MainActivity", "All permissions granted after returning from Settings, starting service")
            startChargingDetectionService()
        }

        if (!preferenceManager.isOnboardingCompleted()) {
            return
        }

        val locationJustEnabled = hasRequiredPermissions && !previousPermissions

        if (locationJustEnabled) {
            Log.d("MainActivity", "Location permission just enabled - notifying ViewModel")
            try {
                val viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
                viewModel.dispatch(ActivityIntent.LocationEnabled)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to notify ViewModel of location change: ${e.message}")
            }
        } else {
            Log.d("MainActivity", "No permission change detected - skipping refresh")
        }
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.setAppInForeground(false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            handleLocationPermissionRequest(it)
            handleDeepLink(it)
            checkStationCodeIntent(it)
        }
    }

    private fun checkStationCodeIntent(intent: Intent?) {
        Log.d(
            "MainActivity",
            "Checking intent: Action=${intent?.action}, Extras=${intent?.extras?.keySet()}"
        )

        if (intent?.action == BatteryServiceActions.OPEN_STATION_CODE ||
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