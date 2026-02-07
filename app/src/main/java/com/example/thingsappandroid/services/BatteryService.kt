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
import android.util.Log
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
import com.example.thingsappandroid.util.ClimateUtils
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BatteryService : Service() {


    private  var tag: String = "BatteryServiceLog";
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

    /** True after broadcast receiver is registered in onStartCommand (after startForeground). Avoids delivery before foreground. */
    private var receiverRegistered = false

    /** Debounce rapid WiFi/location broadcasts (system often fires many for one user action). */
    private var connectivityDebounceJob: Job? = null
    private val connectivityDebounceMs = 500L

    // Device Climate Status
    private var currentDCS: DeviceClimateStatus = DeviceClimateStatus.UNKNOWN

    // Utility managers
    private lateinit var consumptionTracker: ConsumptionTracker
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
                BatteryServiceActions.DEVICEINFO_UPDATED -> updateNotification()
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> handleConnectivityIntent(intent)
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION,
                LocationManager.PROVIDERS_CHANGED_ACTION,
                LocationManager.MODE_CHANGED_ACTION -> {
                    connectivityDebounceJob?.cancel()
                    connectivityDebounceJob = serviceScope.launch {
                        delay(connectivityDebounceMs)
                        connectivityDebounceJob = null
                        handleConnectivityIntent(intent)
                    }
                }
            }
        }
    }

  
    private fun handleConnectivityIntent(intent: Intent?, fromInitialFetch: Boolean = false) {

        // log which intent action called
        Log.d(tag,"handleConnectivityIntent ${intent?.action.toString()}")

        // Parse battery and update chargeState when it's a battery intent
        if (isBatteryIntent(intent?.action)) {
            handleBatteryIntent(intent!!)
        }

        serviceScope.launch {
            val wifiReady = isWiFiConnected(applicationContext)
            val locationReady = LocationUtils.hasLocationPermission(applicationContext) &&
                    LocationUtils.isLocationEnabled(applicationContext)
            val isWifiIntent = isWifiOrConnectivityAction(intent?.action)
            val isPowerConnected = getChargingStatusFromIntent(intent) ?: (chargeState?.isCharging == true)
            val current = WifiUtils.getHashedWiFiBSSID(applicationContext)
            val last = PreferenceManager(applicationContext).getLastWifiBssid()
            val bssidChanged = current != null && current != last

            Log.d(tag,"handleConnectivityIntent wifiReady: $wifiReady locationReady: $locationReady isWifiIntent: $isWifiIntent isPowerConnected: $isPowerConnected")
            Log.d(tag,"BSSID - current: ${current}... last: ${last}... changed: $bssidChanged")

            when {
                wifiReady && locationReady -> onWifiAndLocationReady(isWifiIntent, bssidChanged, isPowerConnected)
                wifiReady && !locationReady -> onWifiReadyLocationMissing()
                else -> onWifiMissing()
            }

            tryShowStationCodeIfNeeded(wifiReady, locationReady, isPowerConnected, fromInitialFetch)
            syncPendingConsumptionsIfSupported()
        }
    }

    /**
     * Checks if the intent action is battery-related.
     */
    private fun isBatteryIntent(action: String?): Boolean =
        action in listOf(
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED
        )

    /**
     * Extracts charging status directly from battery intent if available.
     * Returns null if intent is not a battery intent or doesn't contain battery status.
     */
    private fun getChargingStatusFromIntent(intent: Intent?): Boolean? {
        if (!isBatteryIntent(intent?.action)) return null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return null
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Parses battery intent, updates charge state, and handles charging start/stop logic.
     * - On unplug: stops consumption tracking, resets station code notification flag
     * - On plug: starts consumption tracking and periodic reports
     * - Updates chargeState, generates new groupId, and refreshes notification
     */
    private fun handleBatteryIntent(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        
        val wasCharging = chargeState?.isCharging ?: false
        val isInitialization = chargeState == null
        
        // Only update if state changed or this is the first read
        if (isInitialization || wasCharging != isCharging) {
            // Handle unplug: stop tracking, clear notifications
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
            
            // Handle plug: start tracking and reports
            if (isCharging && !isInitialization) {
                consumptionTracker.startTracking(batteryPct, voltage)
                startPeriodicConsumptionReports()
            }
            
            // Update state and notification
            chargeState = BatteryState(isCharging, voltage, batteryPct, plugged)
            groupId = java.util.UUID.randomUUID().toString()
            updateNotification()
        }
    }

    private fun isWifiOrConnectivityAction(action: String?): Boolean =
        action in listOf(
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
            WifiManager.WIFI_STATE_CHANGED_ACTION,
            ConnectivityManager.CONNECTIVITY_ACTION
        )

    /**
     * WiFi and location are both available. Cancel location nag; run charging/device flow when
     * plug just connected or BSSID changed (new network).
     */
    private suspend fun onWifiAndLocationReady(
        isWifiIntent: Boolean,
        bssidChanged: Boolean,
        isPowerConnected: Boolean
    ) {
        notificationManager.cancel(CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
        
        // Dismiss station code notification when WiFi changes (new network)
        if (bssidChanged) {
            Log.d(tag, "WiFi BSSID changed - dismissing station code notification")
            notificationManager.cancel(STATION_CODE_NOTIFICATION_ID)
            stationCodeNotificationShownThisChargingSession = false
        }
        
        val runChargingFlow = when {
            isPowerConnected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> true
            bssidChanged && isWifiIntent -> true
            else -> false
        }
        if (runChargingFlow) {
            handleChargingStartedInternal()
            updateNotification()
        }
    }

    /**
     * WiFi on but location off. Show "enable location" only when app is in background.
     */
    private fun onWifiReadyLocationMissing() {
        if (PreferenceManager(applicationContext).getAppInForeground()) {
            notificationManager.cancel(CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
        } else {
            showLocationRequiredNotification()
        }
    }

    /**
     * WiFi not connected. Clear location notification.
     */
    private fun onWifiMissing() {
        notificationManager.cancel(CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
    }

    /**
     * Show station-code notification at most once per session when plug connected (or initial fetch while charging).
     */
    private suspend fun tryShowStationCodeIfNeeded(
        wifiReady: Boolean,
        locationReady: Boolean,
        isPowerConnected: Boolean,
        fromInitialFetch: Boolean
    ) {
        val isPlugOrInitialCharging = isPowerConnected ||
                (fromInitialFetch && chargeState?.isCharging == true)
        if (wifiReady && locationReady && isPlugOrInitialCharging) {
            maybeShowStationCodeNotificationOnce()
        }
    }

    private suspend fun syncPendingConsumptionsIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            consumptionTracker.syncPendingConsumptions()
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
            addAction(BatteryServiceActions.DEVICEINFO_UPDATED)
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

            stationCodeHandler = StationCodeHandler(this)
            climateStatusManager = ClimateStatusManager(this)
            notificationHelper = BatteryNotificationHelper(this, notificationManager)


            createNotificationChannel()
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

        // Register receiver only after startForeground() so we are in valid foreground state before receiving broadcasts (avoids "Handler on dead thread" when process was killed before startForeground).
        if (!receiverRegistered) {
            registerBroadcastReceiver()
            receiverRegistered = true
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
                    resources.getDrawable(R.drawable.ic_climate_status, null)
                    R.drawable.ic_climate_status
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
     * Called from: DEVICEINFO_UPDATED broadcast (after getDeviceInfo API + save), charge-state change in handleConnectivityIntent,
     * after handleChargingStartedInternal(), and onStartCommand. No periodic timer.
     */
    private fun updateNotification() {
        serviceScope.launch {
            try {
                val prefs = PreferenceManager(applicationContext)
                val cachedDeviceInfo = prefs.getLastDeviceInfo()
                val apiClimateStatus = cachedDeviceInfo?.climateStatus
                
                // Dismiss station code notification if:
                // 1. Station is connected (hasStation = true)
                // 2. OR climate status is green (no station code needed)
                val hasStation = prefs.getHasStation()
                val isGreen = apiClimateStatus != null && apiClimateStatus in API_GREEN_CLIMATE_STATUSES
                
                if (hasStation || isGreen) {
                    Log.d(tag, "Dismissing station code notification - hasStation: $hasStation, isGreen: $isGreen")
                    notificationManager.cancel(STATION_CODE_NOTIFICATION_ID)
                    stationCodeNotificationShownThisChargingSession = false
                }

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
                        resources.getDrawable(R.drawable.ic_climate_status, null)
                        R.drawable.ic_climate_status
                    } catch (e: Exception) {
                        android.R.drawable.ic_dialog_info
                    }
                    Triple(data.title, color, icon)
                } else {
                    // Don't show climate result until GetDeviceInfo is available; show loading instead
                    description = "Getting your climate data..."
                    val iconRes = try {
                        resources.getDrawable(R.drawable.ic_climate_status, null)
                        R.drawable.ic_climate_status
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
            if (receiverRegistered) {
                unregisterReceiver(internalBroadcastReceiver)
                receiverRegistered = false
            }
        } catch (e: Exception) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "BatteryService.onDestroy")
                scope.level = io.sentry.SentryLevel.WARNING
                Sentry.captureException(e)
            }
        }
        serviceScope.cancel()
    }


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
