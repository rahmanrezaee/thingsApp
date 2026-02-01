package com.example.thingsappandroid.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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


data class BatteryState(
    val isCharging: Boolean,
    val voltage: Int, // in millivolts
    val level: Int, // battery level 0-100
    val plugged: Int // charging type
) {
    fun plugged(): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "UNPLUGGED"
        }
    }
}


class BatteryService : Service() {

    private val TAG = "BatteryService"
    private lateinit var deviceId: String
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var contentIntent: PendingIntent
    private var intervalRate: Int = 1 // seconds - update every second
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var chargeState: BatteryState? = null
    private var groupId: String? = null
    private var currentStationCode: String? = null

    // Device Climate Status
    private var currentDCS: DeviceClimateStatus = DeviceClimateStatus.UNKNOWN

    // Utility managers
    private lateinit var consumptionTracker: ConsumptionTracker
    private lateinit var wifiAddressMonitor: WifiAddressMonitor
    private lateinit var stationCodeHandler: StationCodeHandler
    private lateinit var climateStatusManager: ClimateStatusManager

    /**
     * Internal broadcast receiver - listens to broadcasts from external receivers
     */
    private val internalBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Station code updates
                "com.example.thingsappandroid.STATION_CODE_UPDATED" -> {
                    Log.d(TAG, "📍 Station code updated from user")
                    val code = intent.getStringExtra("station_code")
                    if (code != null) {
                        currentStationCode = code

                        // Handle station code submission flow
                        serviceScope.launch {
                            stationCodeHandler.handleStationCodeSubmission(
                                deviceId = deviceId,
                                stationCode = code,
                                onNotificationNeeded = {
                                    showStationCodeNotification()
                                }
                            )
                        }
                    }
                }

                "com.example.thingsappandroid.HAS_STATION_UPDATED" -> {
                    Log.d(
                        TAG,
                        "Device has station from getDeviceInfo, cancelling Station Code notification"
                    )
                    notificationManager.cancel(STATION_CODE_NOTIFICATION_ID)
                }

                // Battery changes (from BatteryChangeReceiver)
                "com.example.thingsappandroid.BATTERY_CHANGED" -> {
                    val isCharging = intent.getBooleanExtra("is_charging", false)
                    val level = intent.getIntExtra("level", -1)
                    val voltage = intent.getIntExtra("voltage", -1)
                    val plugged = intent.getIntExtra("plugged", -1)

                    Log.d(
                        TAG,
                        "Battery changed - Charging: $isCharging, Level: $level%, Voltage: ${voltage}mV"
                    )

                    val wasCharging = chargeState?.isCharging ?: false
                    val isInitialization = chargeState == null

                    // Detect charging state changes
                    if (isInitialization || wasCharging != isCharging) {
                        Log.i(
                            TAG,
                            "Battery State Change - was=$wasCharging, is=$isCharging, init=$isInitialization"
                        )

                        // Charging stopped
                        if (!isCharging && wasCharging) {
                            notificationManager.cancel(STATION_CODE_NOTIFICATION_ID)
                            sendBackgroundData()
                            // Stop consumption tracking and save final measurement
                            serviceScope.launch {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    consumptionTracker.stopTracking(level, voltage)
                                }
                            }
                            // Show full-screen notification with WiFi and Location status
                            showChargingStoppedNotification()
                        }

                        // Charging started (transition from not charging to charging)
                        if (isCharging && !wasCharging && !isInitialization) {
                            serviceScope.launch {
                                val climateStatus = handleChargingStarted()
                                if (climateStatus != null && climateStatus !in listOf(5, 6, 7, 9)) {
                                    delay(3000)
                                    showStationCodeNotification()
                                }
                            }
                            // Start consumption tracking
                            consumptionTracker.startTracking(level, voltage)
                            // Schedule periodic consumption reports
                            startPeriodicConsumptionReports()
                        }
                        // Note: Initialization handling is done in fetchInitialBatteryState()

                        // Update charge state
                        chargeState = BatteryState(
                            isCharging = isCharging,
                            voltage = voltage,
                            level = level,
                            plugged = plugged
                        )
                        groupId = java.util.UUID.randomUUID().toString()

                        // Update DCS based on charging state
                        updateDeviceClimateStatus(chargeState!!)
                        updateNotification()
                    }
                }

                // WiFi changes (from WiFiChangeReceiver)
                "com.example.thingsappandroid.WIFI_CHANGED" -> {
                    val isConnected = intent.getBooleanExtra("is_connected", false)
                    val bssid = intent.getStringExtra("bssid")
                    Log.d(TAG, "WiFi changed - Connected: $isConnected, BSSID: ${bssid?.take(10)}")

                    if (isConnected && bssid != null) {
                        serviceScope.launch {
                            val deviceId = DeviceUtils.getStoredDeviceId(applicationContext)
                            val (latitude, longitude) = LocationUtils.getLocationCoordinates(
                                applicationContext
                            ) ?: Pair(0.0, 0.0)
                            val isCharging = chargeState?.isCharging == true

                            // Check if WiFi address changed
                            if (wifiAddressMonitor.hasWifiChanged(bssid)) {
                                Log.i(
                                    TAG,
                                    "📡 WiFi Address Changed! Old: ${
                                        wifiAddressMonitor.getLastKnownAddress()?.take(10)
                                    }..., New: ${bssid.take(10)}..."
                                )
                                wifiAddressMonitor.handleWifiAddressChange(
                                    deviceId,
                                    bssid,
                                    latitude,
                                    longitude,
                                    isCharging
                                )
                            }

                            // Update last known WiFi address
                            wifiAddressMonitor.updateLastKnownAddress(bssid)

                            // If charging, refresh climate status
                            if (isCharging) {
                                Log.d(
                                    TAG,
                                    "WiFi connected while charging, refreshing climate status"
                                )
                                handleChargingStarted()
                            }

                            // Try to sync pending consumptions
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                consumptionTracker.syncPendingConsumptions()
                            }
                        }
                    }
                }

                // Location changes (from LocationChangeReceiver)
                "com.example.thingsappandroid.LOCATION_CHANGED" -> {
                    val isEnabled = intent.getBooleanExtra("is_enabled", false)
                    Log.d(TAG, "Location services changed - Enabled: $isEnabled")

                    // If location enabled and device is charging, refresh climate status
                    if (isEnabled && chargeState?.isCharging == true) {
                        serviceScope.launch {
                            Log.d(TAG, "Location enabled while charging, refreshing climate status")
                            handleChargingStarted()
                            // Try to sync pending consumptions
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                consumptionTracker.syncPendingConsumptions()
                            }
                        }
                    } else if (isEnabled) {
                        // Location enabled but not charging, still try to sync
                        serviceScope.launch {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                consumptionTracker.syncPendingConsumptions()
                            }
                        }
                    }
                }

                "com.example.thingsappandroid.MANUAL_SYNC_REQUESTED" -> {
                    Log.d(TAG, "Manual sync requested from UI")
                    serviceScope.launch {
                        handleChargingStarted() // Re-fetch device info, climate status
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            consumptionTracker.syncPendingConsumptions() // Sync any pending consumptions
                        }
                    }
                }

                "com.example.thingsappandroid.BATTERY_STATE_REQUEST" -> {
                    Log.d(TAG, "Battery state requested from UI - broadcasting current state")
                    broadcastCurrentBatteryState()
                }
            }
        }
    }

    /**
     * Broadcast current battery state to all listeners (HomeViewModel)
     */
    private fun broadcastCurrentBatteryState() {
        try {
            val currentState = chargeState
            if (currentState != null) {
                // Use the stored charge state
                val batteryIntent = Intent("com.example.thingsappandroid.BATTERY_CHANGED").apply {
                    putExtra("is_charging", currentState.isCharging)
                    putExtra("level", currentState.level)
                    putExtra("voltage", currentState.voltage)
                    putExtra("plugged", currentState.plugged)
                }
                sendBroadcast(batteryIntent)
                Log.d(TAG, "Broadcasted current battery state: ${currentState.level}%, Charging: ${currentState.isCharging}")
            } else {
                // No state available yet, fetch and broadcast
                Log.w(TAG, "No charge state available, fetching fresh battery state")
                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent != null) {
                    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
                    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

                    val broadcastIntent = Intent("com.example.thingsappandroid.BATTERY_CHANGED").apply {
                        putExtra("is_charging", isCharging)
                        putExtra("level", batteryPct)
                        putExtra("voltage", voltage)
                        putExtra("plugged", plugged)
                    }
                    sendBroadcast(broadcastIntent)
                    Log.d(TAG, "Broadcasted fresh battery state: ${batteryPct}%, Charging: $isCharging")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting battery state", e)
        }
    }

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        try {
            super.onCreate()
            context = this
            batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

            // Initialize utility managers
            consumptionTracker = ConsumptionTracker(this, deviceId, batteryManager)
            wifiAddressMonitor = WifiAddressMonitor(this)
            stationCodeHandler = StationCodeHandler(this)
            climateStatusManager = ClimateStatusManager(this)

            // Create notification channel
            createNotificationChannel()

            // Register internal broadcast receiver for all events
            val internalFilter = IntentFilter().apply {
                addAction("com.example.thingsappandroid.STATION_CODE_UPDATED")
                addAction("com.example.thingsappandroid.HAS_STATION_UPDATED")
                addAction("com.example.thingsappandroid.BATTERY_CHANGED")
                addAction("com.example.thingsappandroid.WIFI_CHANGED")
                addAction("com.example.thingsappandroid.LOCATION_CHANGED")
                addAction("com.example.thingsappandroid.MANUAL_SYNC_REQUESTED")
                addAction("com.example.thingsappandroid.BATTERY_STATE_REQUEST")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    this,
                    internalBroadcastReceiver,
                    internalFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(internalBroadcastReceiver, internalFilter)
            }

            Log.d(TAG, "Internal broadcast receiver registered for all events")

            // NEW: Fetch initial battery state immediately so we don't wait for a broadcast
            fetchInitialBatteryState()

            // NEW: For Android 14+, start aggressive notification monitoring
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startNotificationMonitoring()
            }

            Log.d(TAG, "BatteryService onCreate completed successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in BatteryService onCreate", e)
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
            Log.e(TAG, "Error in BatteryService onCreate", e)
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
        Log.d(TAG, "=== onStartCommand called - Service is starting ===")
        Log.d(
            TAG,
            "Android Version: ${Build.VERSION.SDK_INT}, Device: ${Build.MANUFACTURER} ${Build.MODEL}"
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(
                TAG,
                "Service requires Android 11+ (API 30), but device is API ${Build.VERSION.SDK_INT}"
            )
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
            val initialDcsInfo = getDeviceClimateStatusInfo()
            Log.d(TAG, "Initial DCS: ${currentDCS.name}, Color: ${initialDcsInfo.color}")

            notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(initialDcsInfo.iconRes)
                .setColor(initialDcsInfo.color)
                .setContentTitle(initialDcsInfo.title)
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

            Log.d(TAG, "Starting foreground service with notification ID: $NOTIFICATION_ID")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            Log.d(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating initial notification: ${e.message}", e)

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
            Log.d(TAG, "Fallback notification created")
        }

        // Start periodic updates (battery changes are handled by batteryBroadcastReceiver)
        try {
            startPeriodicUpdates()
            startPeriodicDeviceInfoRefresh()
            updateNotification()
            Log.d(TAG, "Periodic updates and notification initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting periodic updates: ${e.message}", e)
            // Don't throw - service should continue running even if monitoring fails
        }

        Log.d(TAG, "onStartCommand completed, returning START_STICKY")
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
                Log.d(TAG, "Notification channel created: $CHANNEL_ID")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel: ${e.message}", e)
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
            while (true) {
                delay(200)

                // Only recreate when we're supposed to be showing (charging). Don't show while not charging.
                if (chargeState?.isCharging != true) continue

                val activeNotifications = notificationManager.activeNotifications
                val ourNotificationExists = activeNotifications.any { it.id == NOTIFICATION_ID }

                if (!ourNotificationExists) {
                    Log.w(TAG, "Notification was dismissed! Recreating immediately...")
                    recreateForegroundNotification()
                }
            }
        }
    }

    /**
     * NEW: Recreates the foreground notification immediately
     * Used when dismissal is detected on Android 14+
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun recreateForegroundNotification() {
        try {
            val dcsInfo = getDeviceClimateStatusInfo()

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(dcsInfo.iconRes)
                .setColor(dcsInfo.color)
                .setContentTitle(dcsInfo.title)
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

            Log.d(
                TAG,
                "Foreground notification recreated successfully with DCS: ${currentDCS.name}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate foreground notification: ${e.message}", e)
        }
    }

    /**
     * Starts periodic notification updates.
     * Battery state changes are now handled by batteryBroadcastReceiver.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun startPeriodicUpdates() {
        // Periodic notification updates - every second
        serviceScope.launch {
            while (true) {
                delay(intervalRate * 1000L)

                // Update DCS periodically
                chargeState?.let { updateDeviceClimateStatus(it) }
                updateNotification()
            }
        }
    }

    /**
     * Start periodic consumption reports every 5 seconds
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
     * Start periodic device info refresh every minute
     */
    private fun startPeriodicDeviceInfoRefresh() {
        serviceScope.launch {
            while (true) {
                try {
                    val deviceId = DeviceUtils.getStoredDeviceId(applicationContext)
                    val wifiAddress = WifiUtils.getHashedWiFiBSSID(applicationContext)
                    val (latitude, longitude) = LocationUtils.getLocationCoordinates(applicationContext) ?: Pair(0.0, 0.0)
                    val prefManager = PreferenceManager(applicationContext)
                    val stationCode = prefManager.getStationCode()
                    
                    if (deviceId != null) {
                        Log.d(TAG, "Syncing device info from service...")
                        val request = com.example.thingsappandroid.data.model.DeviceInfoRequest(
                            deviceId = deviceId,
                            stationCode = stationCode,
                            wifiAddress = wifiAddress,
                            latitude = latitude,
                            longitude = longitude
                        )
                        val response = com.example.thingsappandroid.data.remote.NetworkModule.api.getDeviceInfo(request)
                        if (response.isSuccessful) {
                            response.body()?.data?.let { deviceInfo ->
                                Log.d(TAG, "Device info synced successfully from service")
                                val prefManager = PreferenceManager(applicationContext)
                                prefManager.saveDeviceInfo(deviceInfo)
                                
                                // Broadcast update
                                val intent = Intent("com.example.thingsappandroid.DEVICE_INFO_UPDATED")
                                sendBroadcast(intent)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic device info refresh: ${e.message}")
                }
                delay(60_000) // Every minute
            }
        }
    }

    /**
     * Updates the Device Climate Status based on battery state
     */
    private fun updateDeviceClimateStatus(batteryState: BatteryState) {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            if (batteryIntent != null) {
                val temperature = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    -1
                ) / 10.0 // Celsius
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val voltage =
                    batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0 // Volts
                val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

                // Determine DCS based on battery temperature, health, and charging state
                currentDCS = when {
                    temperature > 45 || health == BatteryManager.BATTERY_HEALTH_OVERHEAT ->
                        DeviceClimateStatus.CRITICAL

                    temperature > 40 || health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ->
                        DeviceClimateStatus.WARNING

                    batteryState.isCharging && !currentStationCode.isNullOrBlank() && temperature < 35 ->
                        DeviceClimateStatus.EXCELLENT

                    batteryState.isCharging && temperature < 38 ->
                        DeviceClimateStatus.GOOD

                    // Not charging: keep previous status
                    !batteryState.isCharging -> currentDCS

                    else -> DeviceClimateStatus.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device climate status: ${e.message}", e)
            currentDCS = DeviceClimateStatus.UNKNOWN
        }
    }

    /**
     * Gets the current Device Climate Status information with icon, color, and title.
     */
    private fun getDeviceClimateStatusInfo(): DCSInfo {
        val iconRes = try {
            resources.getDrawable(R.drawable.ic_power, null)
            R.drawable.ic_power
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }

        return when (currentDCS) {
            DeviceClimateStatus.EXCELLENT -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF4CAF50.toInt(), // Green
                title = "Green",
                description = "Charging with green energy",
                detailedDescription = "Your device is charging optimally with verified renewable energy."
            )

            DeviceClimateStatus.GOOD -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF4CAF50.toInt(), // Green
                title = "Green",
                description = "Charging normally",
                detailedDescription = "Your device is charging with good conditions."
            )

            DeviceClimateStatus.NORMAL -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF9E9E9E.toInt(), // Gray (neutral)
                title = "Not green",
                description = "Device operating normally",
                detailedDescription = "Your device is operating normally."
            )

            DeviceClimateStatus.WARNING -> DCSInfo(
                iconRes = iconRes,
                color = 0xFFFF9800.toInt(), // Orange
                title = "1.5°C Aligned",
                description = "Battery temperature elevated",
                detailedDescription = "Battery temperature is elevated."
            )

            DeviceClimateStatus.CRITICAL -> DCSInfo(
                iconRes = iconRes,
                color = 0xFFF44336.toInt(), // Red
                title = "1.5°C Aligned",
                description = "Battery overheating!",
                detailedDescription = "Battery temperature is critically high."
            )

            DeviceClimateStatus.UNKNOWN -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF9E9E9E.toInt(), // Gray (neutral)
                title = "Not green",
                description = "Monitoring device status",
                detailedDescription = "Monitoring device status..."
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun handleChargingStarted(): Int? {
        return try {
            val deviceId = DeviceUtils.getStoredDeviceId(applicationContext)
            val (latitude, longitude) = LocationUtils.getLocationCoordinates(applicationContext)
                ?: Pair(0.0, 0.0)

            // Use ClimateStatusManager to handle charging
            val climateStatus = climateStatusManager.handleChargingStarted()

            // Check WiFi address changes
            val wifiResult = WifiUtils.getHashedWiFiBSSIDWithRetry(
                applicationContext,
                maxRetries = 2,
                delayMs = 1000L
            )
            if (wifiResult.success && wifiResult.bssid != null) {
                if (wifiAddressMonitor.hasWifiChanged(wifiResult.bssid)) {
                    Log.i(TAG, "📡 WiFi Address Changed during charging start!")
                    wifiAddressMonitor.handleWifiAddressChange(
                        deviceId,
                        wifiResult.bssid,
                        latitude,
                        longitude,
                        isCharging = true
                    )
                }
                wifiAddressMonitor.updateLastKnownAddress(wifiResult.bssid)
            }

            climateStatus
        } catch (e: Exception) {
            Log.e(TAG, "Error handling charging event: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "handleChargingEvent")
                Sentry.captureException(e)
            }
            null
        }
    }

    private fun sendBackgroundData() {
        Log.i(TAG, "Charging stopped - would send background data here")
    }

    private fun updateNotification() {
        serviceScope.launch {
            try {
                val prefs = PreferenceManager(applicationContext)
                val apiClimateStatus = prefs.getClimateStatus()

                val (title, colorInt, iconRes) = if (apiClimateStatus != null) {
                    val data = ClimateUtils.getMappedClimateData(apiClimateStatus)
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
                    val dcsInfo = getDeviceClimateStatusInfo()
                    Triple(dcsInfo.title, dcsInfo.color, dcsInfo.iconRes)
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
                Log.e(TAG, "Error updating notification: ${e.message}", e)
            }
        }
    }

    /**
     * Shows station code notification if device is not connected to a station.
     * Allows user to manually enter station code to verify green energy.
     */
    private fun showStationCodeNotification() {
        if (sharedPreferences.getBoolean("has_station", false)) {
            Log.d(TAG, "Station Code notification skipped: device already has station")
            return
        }

        val intentMain = Intent(this, MainActivity::class.java).apply {
            action = "com.example.thingsappandroid.OPEN_STATION_CODE"
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
     * Shows a system notification when charging stops.
     * This only checks Location status (WiFi is assumed always on).
     */
    private fun showChargingStoppedNotification() {
        try {
            // Check Location status only (WiFi is always on)
            val hasLocationPermission = LocationUtils.hasLocationPermission(this)
            val lastLocation = LocationUtils.getLastKnownLocation(this)
            val isLocationAvailable = hasLocationPermission && lastLocation != null
            
            Log.i(TAG, "Charging stopped - Location available: $isLocationAvailable")
            
            // Only show notification if location is not available
            if (!isLocationAvailable) {
                Log.i(TAG, "Location not available - showing notification")
                
                // Create intent to launch the full-screen activity
                val fullScreenIntent = Intent(this, com.example.thingsappandroid.ChargingStatusActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                
                // Create pending intent for the full-screen notification
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    CHARGING_STOPPED_REQUEST_CODE,
                    fullScreenIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                
                // Create the notification channel if needed (for Android O+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHARGING_STATUS_CHANNEL_ID,
                        "Location Status",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notification for location services status when charging stops"
                        enableVibration(true)
                        enableLights(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                
                // Build the notification with action button
                val notificationBuilder = NotificationCompat.Builder(this, CHARGING_STATUS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_power)
                    .setContentTitle("Location Services Required")
                    .setContentText("Please enable location services for accurate charging tracking")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setFullScreenIntent(pendingIntent, true)
                    .setContentIntent(pendingIntent)
                
                // Show the notification
                val notification = notificationBuilder.build()
                notificationManager.notify(CHARGING_STATUS_NOTIFICATION_ID, notification)
                
                Log.i(TAG, "Location notification shown successfully")
            } else {
                Log.i(TAG, "Location is available - no notification needed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing charging stopped notification: ${e.message}", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "showChargingStoppedNotification")
                Sentry.captureException(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(internalBroadcastReceiver)
            Log.d(TAG, "Internal broadcast receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver", e)
            Sentry.withScope { scope ->
                scope.setTag("operation", "BatteryService.onDestroy")
                scope.level = io.sentry.SentryLevel.WARNING
                Sentry.captureException(e)
            }
        }
        serviceScope.cancel()
    }

    // ========================================
    // NOTE: Utility functions moved to separate files:
    // - ConsumptionTracker.kt: Consumption tracking logic
    // - WifiAddressMonitor.kt: WiFi address change detection
    // - StationCodeHandler.kt: Station code submission flow
    // - ClimateStatusManager.kt: Climate status operations
    // ========================================

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
                
                Log.d(TAG, "Initial battery state - Level: $batteryPct%, Charging: $isCharging, Voltage: ${voltage}mV, Plugged: $plugged")
                
                // Initialize charge state
                chargeState = BatteryState(
                    isCharging = isCharging,
                    voltage = voltage,
                    level = batteryPct,
                    plugged = plugged
                )
                
                // If already charging, start consumption tracking immediately
                if (isCharging) {
                    Log.i(TAG, "Device is already charging on service start - starting consumption tracking")
                    consumptionTracker.startTracking(batteryPct, voltage)
                    startPeriodicConsumptionReports()
                    
                    // Update DCS
                    updateDeviceClimateStatus(chargeState!!)
                    updateNotification()
                }
                
                // Broadcast initial battery state to HomeViewModel
                val initialBatteryIntent = Intent("com.example.thingsappandroid.BATTERY_CHANGED").apply {
                    putExtra("is_charging", isCharging)
                    putExtra("level", batteryPct)
                    putExtra("voltage", voltage)
                    putExtra("plugged", plugged)
                }
                sendBroadcast(initialBatteryIntent)
                Log.d(TAG, "Broadcasted initial battery state to UI")
            } else {
                Log.w(TAG, "Could not get initial battery state - registerReceiver returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching initial battery state", e)
        }
    }

    companion object {
        private const val CHANNEL_ID = "battery_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val STATION_CODE_CHANNEL_ID = "station_code_channel"
        private const val STATION_CODE_NOTIFICATION_ID = 2
        private const val STATION_CODE_GROUP_KEY = "com.example.thingsappandroid.station_code_group"
        private const val CHARGING_STATUS_CHANNEL_ID = "charging_status_channel"
        private const val CHARGING_STATUS_NOTIFICATION_ID = 3
        private const val CHARGING_STOPPED_REQUEST_CODE = 100
    }
}

/**
 * Device Climate Status levels based on battery conditions and charging state.
 */
enum class DeviceClimateStatus {
    EXCELLENT,  // Green energy charging with optimal conditions
    GOOD,       // Normal charging with good conditions
    NORMAL,     // Normal operation
    WARNING,    // Elevated temperature or suboptimal conditions
    CRITICAL,   // Overheating or critical battery issues
    UNKNOWN     // Status not yet determined
}

/**
 * Device Climate Status information for notification display.
 */
data class DCSInfo(
    val iconRes: Int,
    val color: Int,
    val title: String,
    val description: String,
    val detailedDescription: String
)
