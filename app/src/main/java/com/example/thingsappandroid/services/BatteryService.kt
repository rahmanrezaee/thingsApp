package com.example.thingsappandroid.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.R
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.services.utils.BatteryNotificationHelper
import com.example.thingsappandroid.services.utils.ClimateStatusManager
import com.example.thingsappandroid.services.utils.ConsumptionTracker
import com.example.thingsappandroid.services.utils.StationCodeHandler
import com.example.thingsappandroid.services.utils.WifiAddressMonitor
import com.example.thingsappandroid.util.ClimateUtils
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BatteryService : Service() {

    private lateinit var deviceId: String
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var contentIntent: PendingIntent
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private var chargeState: BatteryState? = null
    private var groupId: String? = null
    private var currentStationCode: String? = null
    /** Only show station code notification once per charging session; reset on unplug. */
    private var stationCodeNotificationShownThisChargingSession = false

    // Device Climate Status
    private var currentDCS: DeviceClimateStatus = DeviceClimateStatus.UNKNOWN

    // Utility managers
    private lateinit var consumptionTracker: ConsumptionTracker
    private lateinit var wifiAddressMonitor: WifiAddressMonitor
    private lateinit var stationCodeHandler: StationCodeHandler
    private lateinit var climateStatusManager: ClimateStatusManager
    private lateinit var notificationHelper: BatteryNotificationHelper

    /**
     * Single broadcast receiver for all app and system events.
     */
    private val internalBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: "null"
            when (action) {
                BatteryServiceActions.HAS_STATION_UPDATED -> updateNotification()
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION,
                LocationManager.PROVIDERS_CHANGED_ACTION,
                LocationManager.MODE_CHANGED_ACTION -> handleConnectivityIntent(intent)
                else -> { }
            }
        }
    }

    /**
     * Single handler for battery, wifi, and location intents.
     * 1. If battery intent: update chargeState, handle charging start/stop
     * 2. Check wifi and location - if both available -> call SetClimateStatus first
     * 3. If only location not enabled -> show notification to enable
     * 4. Save consumption not sent to server (sync pending to local)
     * Station code notification is only triggered when [fromInitialFetch] is true (service start while charging) or action is ACTION_POWER_CONNECTED.
     */
    private fun handleConnectivityIntent(intent: Intent?, fromInitialFetch: Boolean = false) {
        // Parse battery and update chargeState when it's a battery intent
        if (intent?.action in listOf(
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED
            )) {
            val i = intent!!
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val wasCharging = chargeState?.isCharging ?: false
            val isInitialization = chargeState == null
            if (isInitialization || wasCharging != isCharging) {
                if (!isCharging && wasCharging) {
                    stationCodeNotificationShownThisChargingSession = false
                    notificationManager.cancel(STATION_CODE_NOTIFICATION_ID)
                    notificationManager.cancel(CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
                    serviceScope.launch {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            consumptionTracker.stopTracking(level, voltage)
                        }
                    }
                }
                if (isCharging && !isInitialization) {
                    consumptionTracker.startTracking(batteryPct, voltage)
                    startPeriodicConsumptionReports()
                }
                chargeState = BatteryState(isCharging, voltage, batteryPct, plugged)
                groupId = java.util.UUID.randomUUID().toString()
                updateNotification()
            }
        }

        serviceScope.launch {
            val wifiOk = isWiFiConnected(applicationContext)
            val locationOk = LocationUtils.hasLocationPermission(applicationContext) &&
                    LocationUtils.isLocationEnabled(applicationContext)

            // When wifi+location ok and charging: call SetClimateStatus (suspend until done), then update notification
            if (wifiOk && locationOk) {
                notificationManager.cancel(CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
                if (chargeState?.isCharging == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    handleChargingStartedInternal()
                    updateNotification()
                }
            } else if (wifiOk && !locationOk) {
                showLocationRequiredNotification()
            } else if (!wifiOk) {
                notificationManager.cancel(CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
            }

            // Station code only when wifi is on and location on and charger was just connected
            val isPlugEventOrInitialCharging = intent?.action == Intent.ACTION_POWER_CONNECTED ||
                (fromInitialFetch && chargeState?.isCharging == true)
            if (wifiOk && locationOk && isPlugEventOrInitialCharging) {
                maybeShowStationCodeNotificationOnce()
            }

            // Save consumption not sent to server (sync: upload if possible, else keep in local)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                consumptionTracker.syncPendingConsumptions()
            }
        }
    }

    private fun showLocationRequiredNotification() {
        try {
            val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                this, CHARGING_STARTED_LOCATION_REQUEST_CODE, settingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    LOCATION_PERMISSION_CHANNEL_ID, "Location Permission",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Enable location for carbon tracking"
                    setShowBadge(true)
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            val builder = NotificationCompat.Builder(this, LOCATION_PERMISSION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle("Enable Location")
                .setContentText("Enable location for carbon tracking")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
            notificationManager.notify(CHARGING_STARTED_LOCATION_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(BatteryServiceActions.HAS_STATION_UPDATED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, internalBroadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(internalBroadcastReceiver, filter)
        }
    }

    private fun isWiFiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            ni?.type == ConnectivityManager.TYPE_WIFI && ni.isConnected
        }
    }

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        try {
            super.onCreate()
            context = this
            batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

            consumptionTracker = ConsumptionTracker(this, deviceId, batteryManager)
            wifiAddressMonitor = WifiAddressMonitor(this)
            stationCodeHandler = StationCodeHandler(this)
            climateStatusManager = ClimateStatusManager(this)
            notificationHelper = BatteryNotificationHelper(this, notificationManager)


            createNotificationChannel()
            registerBroadcastReceiver()
            fetchInitialBatteryState()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startNotificationMonitoring()
            }
        } catch (e: SecurityException) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "BatteryService.onCreate")
                scope.setTag("error_type", "SecurityException")
                scope.setContexts(
                    "service", mapOf(
                        "manufacturer" to Build.MANUFACTURER,
                        "model" to Build.MODEL,
                        "sdk_int" to Build.VERSION.SDK_INT.toString()
                    )
                )
                Sentry.captureException(e)
            }
            Toast.makeText(
                this,
                "ERROR: BatteryService Security - ${e.message} (${Build.MANUFACTURER} ${Build.MODEL} API${Build.VERSION.SDK_INT})",
                Toast.LENGTH_LONG
            ).show()
            throw e
        } catch (e: Exception) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "BatteryService.onCreate")
                scope.setContexts(
                    "service", mapOf(
                        "manufacturer" to Build.MANUFACTURER,
                        "model" to Build.MODEL,
                        "sdk_int" to Build.VERSION.SDK_INT.toString()
                    )
                )
                Sentry.captureException(e)
            }
            Toast.makeText(
                this,
                "ERROR: BatteryService ${e.javaClass.simpleName} - ${e.message} (${Build.MANUFACTURER} ${Build.MODEL} API${Build.VERSION.SDK_INT})",
                Toast.LENGTH_LONG
            ).show()
            throw e
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            stopSelf()
            return START_NOT_STICKY
        }

        val intentMain = Intent(this, MainActivity::class.java)
        intentMain.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        )

        contentIntent = PendingIntent.getActivity(
            this,
            1, intentMain,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            val hasCachedDeviceInfo = PreferenceManager(this).getLastDeviceInfo() != null
            val initialTitle: String
            val initialDescription: String
            val initialColor: Int
            val initialIconRes: Int
            if (hasCachedDeviceInfo) {
                val dcsInfo = notificationHelper.getDCSInfo(currentDCS)
                val cached = PreferenceManager(this).getLastDeviceInfo()
                val status = cached?.climateStatus
                if (status != null) {
                    val data = ClimateUtils.getMappedClimateData(status)
                    initialTitle = data.title
                    initialDescription = data.description
                    initialColor = when (status) {
                        8 -> 0xFFFF9800.toInt()
                        5, 6, 7, 9 -> 0xFF4CAF50.toInt()
                        else -> 0xFF9E9E9E.toInt()
                    }
                    initialIconRes = dcsInfo.iconRes
                } else {
                    initialTitle = dcsInfo.title
                    initialDescription = dcsInfo.description
                    initialColor = dcsInfo.color
                    initialIconRes = dcsInfo.iconRes
                }
            } else {
                val dcsInfo = notificationHelper.getDCSInfo(currentDCS)
                initialTitle = "Checking climate status..."
                initialDescription = "Getting your climate data..."
                initialColor = 0xFF9E9E9E.toInt()
                initialIconRes = dcsInfo.iconRes
            }

            notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(initialIconRes)
                .setColor(initialColor)
                .setContentTitle(initialTitle)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)

            var initialNotification = notification.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                initialNotification.flags = initialNotification.flags or Notification.FLAG_NO_CLEAR
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            // Fallback to basic notification
            notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Not green")
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)

            var fallbackNotification = notification.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                fallbackNotification.flags =
                    fallbackNotification.flags or Notification.FLAG_NO_CLEAR
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    fallbackNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, fallbackNotification)
            }
        }

        // Start periodic updates (battery changes are handled by batteryBroadcastReceiver)
        try {
            updateNotification()
        } catch (e: Exception) {
            Sentry.captureException(e)
        }

        // Start consumption tracking when service starts if already charging
        chargeState?.let { state ->
            if (state.isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                consumptionTracker.startTracking(state.level, state.voltage)
                startPeriodicConsumptionReports()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val batteryChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Device Climate Status",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows current device climate status"
                    setShowBadge(true)
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setBypassDnd(false)
                    }
                }
                notificationManager.createNotificationChannel(batteryChannel)

                val locationPermissionChannel = NotificationChannel(
                    LOCATION_PERMISSION_CHANNEL_ID,
                    "Location Permission",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when location permission is needed to verify charging station"
                    setShowBadge(true)
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(locationPermissionChannel)


            } catch (e: Exception) {
                Sentry.captureException(e)
            }

            val stationCodeChannel = NotificationChannel(
                STATION_CODE_CHANNEL_ID,
                "Station Code",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Station code input and display"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(stationCodeChannel)
        }
    }

    /**
     * NEW: Starts aggressive notification monitoring for Android 14+
     * Monitors for notification dismissals every 200ms and recreates immediately
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startNotificationMonitoring() {
        serviceScope.launch {
            var consecutiveMisses = 0
            while (true) {
                delay(2000) // Check every 2 seconds instead of 200ms

                // Only check when charging
                if (chargeState?.isCharging != true) {
                    consecutiveMisses = 0
                    continue
                }

                val activeNotifications = notificationManager.activeNotifications
                val ourNotificationExists = activeNotifications.any { it.id == NOTIFICATION_ID }

                if (!ourNotificationExists) {
                    consecutiveMisses++
                    // Only recreate after 2 consecutive misses (4 seconds) to avoid false positives
                    if (consecutiveMisses >= 2) {
                        recreateForegroundNotification()
                        consecutiveMisses = 0
                    }
                } else {
                    consecutiveMisses = 0
                }
            }
        }
    }

    /**
     * Recreates the foreground notification when dismissal is detected on Android 14+.
     * Uses cached device info (same as updateNotification) so the recreated notification
     * shows correct Green/Not green instead of always "Not green" from UNKNOWN DCS.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun recreateForegroundNotification() {
        try {
            val prefs = PreferenceManager(applicationContext)
            val apiClimateStatus = prefs.getLastDeviceInfo()?.climateStatus
            val (title, colorInt, iconRes) = if (apiClimateStatus != null) {
                val data = ClimateUtils.getMappedClimateData(apiClimateStatus)
                val color = when (apiClimateStatus) {
                    8 -> 0xFFFF9800.toInt()
                    5, 6, 7, 9 -> 0xFF4CAF50.toInt()
                    else -> 0xFF9E9E9E.toInt()
                }
                val icon = try {
                    resources.getDrawable(R.drawable.ic_power, null)
                    R.drawable.ic_power
                } catch (e: Exception) {
                    android.R.drawable.ic_dialog_info
                }
                Triple(data.title, color, icon)
            } else {
                val dcsInfo = notificationHelper.getDCSInfo(currentDCS)
                Triple(dcsInfo.title, dcsInfo.color, dcsInfo.iconRes)
            }

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setColor(colorInt)
                .setContentTitle(title)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setDefaults(0)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .clearActions()

            val builtNotification = notificationBuilder.build()
            builtNotification.flags = builtNotification.flags or Notification.FLAG_NO_CLEAR

            startForeground(
                NOTIFICATION_ID,
                builtNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /**
     * Periodic consumption recording (every 10s). Runs independently of broadcasts.
     * Always saves to local DB or sends to API when wifi+location available.
     */
    private fun startPeriodicConsumptionReports() {
        serviceScope.launch {
            while (chargeState?.isCharging == true) {
                delay(consumptionTracker.getIntervalMs())
                val currentState = chargeState
                if (currentState != null && currentState.isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    consumptionTracker.recordConsumption(
                        currentState.level,
                        currentState.voltage,
                        isCharging = true,
                        isFinal = false
                    )
                }
            }
        }
    }

    /**
     * Internal: Calls SetClimateStatus and GetDeviceInfo via ClimateStatusManager.
     * Caller (trySetClimateStatusIfReady) must ensure wifi+location are ready first.
     */
    private suspend fun handleChargingStartedInternal(): Int? {
        return try {
            val result = climateStatusManager.handleChargingStarted(
                onLocationError = { reason, details, isPermissionDenied, isServicesDisabled ->
                    if (chargeState?.isCharging == true) {
                        showLocationErrorNotification(reason, details, isPermissionDenied, isServicesDisabled)
                    }
                }
            )
            result
        } catch (e: Exception) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "handleChargingEvent")
                scope.setContexts("charging", mapOf(
                    "device_id" to (DeviceUtils.getStoredDeviceId(applicationContext) ?: "null"),
                    "station_code" to (currentStationCode ?: "null")
                ))
                Sentry.captureException(e)
            }
            null
        }
    }



    /**
     * Updates the foreground notification (climate status / title).
     * Called from: HAS_STATION_UPDATED, charge-state change in handleConnectivityIntent,
     * after handleChargingStartedInternal(), and onStartCommand. No periodic timer.
     */
    private fun updateNotification() {
        serviceScope.launch {
            try {
                val prefs = PreferenceManager(applicationContext)
                val cachedDeviceInfo = prefs.getLastDeviceInfo()
                val apiClimateStatus = cachedDeviceInfo?.climateStatus

                val description: String
                val (title, colorInt, iconRes) = if (apiClimateStatus != null) {
                    val data = ClimateUtils.getMappedClimateData(apiClimateStatus)
                    description = data.description
                    val color = when (apiClimateStatus) {
                        8 -> 0xFFFF9800.toInt()      // 1.5°C Aligned - orange
                        5, 6, 7, 9 -> 0xFF4CAF50.toInt() // Green
                        else -> 0xFF9E9E9E.toInt()   // Not green - gray
                    }
                    val icon = try {
                        resources.getDrawable(R.drawable.ic_power, null)
                        R.drawable.ic_power
                    } catch (e: Exception) {
                        android.R.drawable.ic_dialog_info
                    }
                    Triple(data.title, color, icon)
                } else {
                    // Don't show climate result until GetDeviceInfo is available; show loading instead
                    description = "Getting your climate data..."
                    val iconRes = try {
                        resources.getDrawable(R.drawable.ic_power, null)
                        R.drawable.ic_power
                    } catch (e: Exception) {
                        android.R.drawable.ic_dialog_info
                    }
                    Triple("Checking climate status...", 0xFF9E9E9E.toInt(), iconRes)
                }

                val updatedNotificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(iconRes)
                    .setColor(colorInt)
                    .setContentTitle(title)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(contentIntent)
                    .setDefaults(0)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setShowWhen(false)
                    .clearActions()

                val builtNotification = updatedNotificationBuilder.build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    builtNotification.flags = builtNotification.flags or Notification.FLAG_NO_CLEAR
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        builtNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, builtNotification)
                }

                notification = updatedNotificationBuilder
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Show station code notification at most once per charging session.
     * Only called on ACTION_POWER_CONNECTED or when service starts and device is already charging.
     */
    private suspend fun maybeShowStationCodeNotificationOnce() {
        if (!shouldShowStationCodeNotification()) return
        delay(3000)
        showStationCodeNotification()
        stationCodeNotificationShownThisChargingSession = true
    }

    /** True when charging, no station yet, climate not green, and not already shown this session. */
    private fun shouldShowStationCodeNotification(): Boolean {
        if (chargeState?.isCharging != true || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (stationCodeNotificationShownThisChargingSession) return false
        val prefs = PreferenceManager(applicationContext)
        if (prefs.getHasStation()) return false
        val status = prefs.getLastDeviceInfo()?.climateStatus
        if (status != null && status in API_GREEN_CLIMATE_STATUSES) return false
        return true
    }

    private fun showStationCodeNotification() {
        if (PreferenceManager(applicationContext).getHasStation()) return

        val intentMain = Intent(this, MainActivity::class.java).apply {
            action = BatteryServiceActions.OPEN_STATION_CODE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_station_code_dialog", true)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            intentMain,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val enterCodeAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Enter Code",
            contentIntent
        ).build()

        val stationCodeNotification = NotificationCompat.Builder(this, STATION_CODE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Station Code")
            .setContentText(
                if (currentStationCode.isNullOrBlank()) "Verify Green Energy"
                else "Station Code: $currentStationCode"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (currentStationCode.isNullOrBlank())
                        "Tap Enter Code to verify your charging session with a station code."
                    else "Current Station: $currentStationCode"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(STATION_CODE_GROUP_KEY)
            .setGroupSummary(false)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(contentIntent)
            .addAction(enterCodeAction)
            .build()

        notificationManager.notify(STATION_CODE_NOTIFICATION_ID, stationCodeNotification)
    }

    /**
     * Shows a notification when location is not available during charging.
     * This prompts the user to enable location services or grant permission.
     */
    private fun showLocationErrorNotification(
        errorReason: String?, 
        errorDetails: String?,
        isPermissionDenied: Boolean,
        isServicesDisabled: Boolean
    ) {
        try {
            // Create intent to open location settings or app settings
            val settingsIntent = when {
                isServicesDisabled -> Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                isPermissionDenied -> Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                else -> Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                LOCATION_ERROR_REQUEST_CODE,
                settingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Create the notification channel if needed (for Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    LOCATION_ERROR_CHANNEL_ID,
                    "Location Error",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for location errors during charging"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val title = if (isServicesDisabled) "Enable Location Services" else "Location Permission Required"
            val content = errorDetails ?: "Please enable location for carbon tracking"
            
            // Build the notification
            val notificationBuilder = NotificationCompat.Builder(this, LOCATION_ERROR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_preferences,
                    "Open Settings",
                    pendingIntent
                )
            
            // Show the notification
            val notification = notificationBuilder.build()
            notificationManager.notify(LOCATION_ERROR_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "showLocationErrorNotification")
                Sentry.captureException(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(internalBroadcastReceiver)
        } catch (e: Exception) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "BatteryService.onDestroy")
                scope.level = io.sentry.SentryLevel.WARNING
                Sentry.captureException(e)
            }
        }
        serviceScope.cancel()
    }

    /**
     * Fetch initial battery state when service starts.
     * This ensures we have battery data immediately instead of waiting for a broadcast.
     */
    private fun fetchInitialBatteryState() {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) {
                    (level * 100 / scale)
                } else {
                    0
                }
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                chargeState = BatteryState(
                    isCharging = isCharging,
                    voltage = voltage,
                    level = batteryPct,
                    plugged = plugged
                )
                if (isCharging) {
                    handleConnectivityIntent(null, fromInitialFetch = true)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    companion object {
        private const val CHANNEL_ID = "battery_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val STATION_CODE_CHANNEL_ID = "station_code_channel"
        private const val STATION_CODE_NOTIFICATION_ID = 2
        private const val STATION_CODE_GROUP_KEY = "com.example.thingsappandroid.station_code_group"
        
        // Full-screen notification for location disabled when charging starts
        private const val CHARGING_STARTED_LOCATION_REQUEST_CODE = 102
        private const val CHARGING_STARTED_LOCATION_NOTIFICATION_ID = 5
        
        private const val LOCATION_ERROR_CHANNEL_ID = "location_error_channel"
        private const val LOCATION_ERROR_NOTIFICATION_ID = 4
        private const val LOCATION_ERROR_REQUEST_CODE = 101

        private const val LOCATION_PERMISSION_CHANNEL_ID = "location_permission_channel"
        private const val LOCATION_PERMISSION_NOTIFICATION_ID = 3
    }
}
