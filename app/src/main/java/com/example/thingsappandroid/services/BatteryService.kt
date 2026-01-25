package com.example.thingsappandroid.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.R
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.model.SetStationRequest
import com.example.thingsappandroid.receivers.StationCodeReceiver
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.TimeUtility
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

/**
 * Background service that monitors battery state and charging events.
 * Shows Device Climate Status (DCS) with appropriate icon and color.
 */
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
    
    private val stationCodeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.thingsappandroid.STATION_CODE_UPDATED") {
                Log.d(TAG, "Station code updated, refreshing notification immediately")
                val code = intent.getStringExtra("station_code")
                if (code != null) {
                    currentStationCode = code
                    showStationCodeNotification()
                }
            }
        }
    }
    
    // NEW: Notification listener to detect dismissals
    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Log.d(TAG, "Notification potentially dismissed, recreating immediately")
                serviceScope.launch {
                    recreateForegroundNotification()
                }
            }
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
            
            // Create notification channel
            createNotificationChannel()
            
            // Register receiver for station code updates
            val filter = IntentFilter("com.example.thingsappandroid.STATION_CODE_UPDATED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    this,
                    stationCodeUpdateReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(stationCodeUpdateReceiver, filter)
            }
            
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
                scope.setContexts("service", mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdk_int" to Build.VERSION.SDK_INT.toString()
                ))
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
                scope.setContexts("service", mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdk_int" to Build.VERSION.SDK_INT.toString()
                ))
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
        Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT}, Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Service requires Android 11+ (API 30), but device is API ${Build.VERSION.SDK_INT}")
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

        // Get initial DCS info to set icon color
        try {
            val initialDcsInfo = getDeviceClimateStatusInfo()
            Log.d(TAG, "Initial DCS: ${currentDCS.name}, Color: ${initialDcsInfo.color}")
            
            // Try to use ic_power, fallback to system icon if it doesn't exist
            val iconRes = try {
                // Check if ic_power exists by trying to get it
                val testIcon = resources.getDrawable(R.drawable.ic_power, null)
                R.drawable.ic_power
            } catch (e: Exception) {
                Log.w(TAG, "ic_power drawable not found, using system icon: ${e.message}")
                android.R.drawable.ic_dialog_info
            }
            
            // Get initial charging state for subtitle
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val isChargingInitial = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
                it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
            } ?: false
            val subtitleInitial = if (isChargingInitial) {
                "Charging"
            } else {
                "" // Empty when not charging
            }
            
            notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconRes) // Use ic_power icon if available, otherwise system icon
                .setColor(initialDcsInfo.color) // Set color based on climate status (green/orange/red/gray)
                .setContentTitle(initialDcsInfo.title) // "Green Climate Status", "No Green", or "Align"
                .setContentText(if (subtitleInitial.isNotEmpty()) subtitleInitial else null) // Just "Charging" or empty
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(false)

            var initialNotification = notification.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                initialNotification.flags = initialNotification.flags or Notification.FLAG_NO_CLEAR
            }

            Log.d(TAG, "Starting foreground service with notification ID: $NOTIFICATION_ID, Icon: $iconRes")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            Log.d(TAG, "Foreground service started successfully - notification should be visible")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating initial notification: ${e.message}", e)
            e.printStackTrace()
            // Fallback to basic notification if DCS info fails
            notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Device Climate Status")
                .setContentText("Monitoring device status...")
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
            
            var fallbackNotification = notification.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                fallbackNotification.flags = fallbackNotification.flags or Notification.FLAG_NO_CLEAR
                startForeground(
                    NOTIFICATION_ID,
                    fallbackNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, fallbackNotification)
            }
            Log.d(TAG, "Fallback notification created and shown")
        }
        
        // Start battery monitoring and update notification
        try {
            showChargingOptionNotification()
            updateNotification()
            Log.d(TAG, "Battery monitoring and notification update initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting battery monitoring: ${e.message}", e)
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
                delay(200) // Check every 200ms for dismissals
                
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
            
            // Get current charging state for subtitle
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val isChargingRecreate = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
                it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
            } ?: false
            val subtitleRecreate = if (isChargingRecreate) {
                "Charging"
            } else {
                "" // Empty when not charging
            }
            
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(dcsInfo.iconRes)
                .setColor(dcsInfo.color)
                .setContentTitle(dcsInfo.title) // "Green Climate Status", "No Green", or "Align"
                .setContentText(if (subtitleRecreate.isNotEmpty()) subtitleRecreate else null) // Just "Charging" or empty
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setDefaults(0) // No sound/vibration on recreation
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

            Log.d(TAG, "Foreground notification recreated successfully with DCS: ${currentDCS.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate foreground notification: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showChargingOptionNotification() {
        BatteryReceiver.observe(this)
            .onEach { batteryState ->
                Log.i(
                    TAG,
                    "ChargingState ${chargeState?.isCharging} ${batteryState.isCharging}"
                )
                
                val wasCharging = chargeState?.isCharging ?: false
                val isCharging = batteryState.isCharging
                val isInitialization = chargeState == null
                
                if (isInitialization || wasCharging != isCharging) {
                    Log.i(TAG, "State Change or Init: was=$wasCharging, is=$isCharging, init=$isInitialization")

                    if (!isCharging && wasCharging) {
                        sendBackgroundData()
                    }

                    if (isCharging && !wasCharging && !isInitialization) {
                        handleChargingStarted()
                        
                        if (currentStationCode.isNullOrBlank()) {
                            serviceScope.launch {
                                delay(3000)
                                showStationCodeNotification()
                            }
                        }
                    } else if (isCharging && isInitialization) {
                        handleChargingStarted()
                    }

                    chargeState = batteryState
                    groupId = UUID.randomUUID().toString()
                    
                    // Update DCS based on charging state
                    updateDeviceClimateStatus(batteryState)
                    updateNotification()
                }
            }
            .launchIn(serviceScope)

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
     * Updates the Device Climate Status based on battery state
     */
    private fun updateDeviceClimateStatus(batteryState: BatteryState) {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            if (batteryIntent != null) {
                val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0 // Celsius
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0 // Volts
                val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                
                // Determine DCS based on multiple factors
                currentDCS = when {
                    // Critical conditions
                    temperature > 45 || health == BatteryManager.BATTERY_HEALTH_OVERHEAT -> 
                        DeviceClimateStatus.CRITICAL
                    
                    temperature > 40 || health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ->
                        DeviceClimateStatus.WARNING
                    
                    // Good conditions - charging with renewable energy
                    batteryState.isCharging && !currentStationCode.isNullOrBlank() && temperature < 35 ->
                        DeviceClimateStatus.EXCELLENT
                    
                    // Normal charging
                    batteryState.isCharging && temperature < 38 ->
                        DeviceClimateStatus.GOOD
                    
                    // Not charging but healthy
                    !batteryState.isCharging && temperature < 35 ->
                        DeviceClimateStatus.NORMAL
                    
                    else -> DeviceClimateStatus.UNKNOWN
                }
                
//                Log.d(TAG, "DCS Updated: $currentDCS (Temp: ${temperature}°C, Level: $level%, Voltage: ${voltage}V)")
            } else {
//                Log.w(TAG, "Battery intent is null, keeping DCS as: $currentDCS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device climate status: ${e.message}", e)
            // Keep current DCS or set to UNKNOWN on error
            currentDCS = DeviceClimateStatus.UNKNOWN
        }
    }
    
    /**
     * Gets the current Device Climate Status information
     */
    private fun getDeviceClimateStatusInfo(): DCSInfo {
        // Try to use ic_power, fallback to system icon if it doesn't exist
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
                title = "Green Climate Status",
                description = "Charging with green energy",
                detailedDescription = "Your device is charging optimally with verified renewable energy."
            )
            DeviceClimateStatus.GOOD -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF4CAF50.toInt(), // Green
                title = "Green Climate Status",
                description = "Charging normally",
                detailedDescription = "Your device is charging with good conditions."
            )
            DeviceClimateStatus.NORMAL -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF9E9E9E.toInt(), // Gray (neutral)
                title = "No Green",
                description = "Device operating normally",
                detailedDescription = "Your device is operating normally."
            )
            DeviceClimateStatus.WARNING -> DCSInfo(
                iconRes = iconRes,
                color = 0xFFFF9800.toInt(), // Orange
                title = "Align",
                description = "Battery temperature elevated",
                detailedDescription = "Battery temperature is elevated."
            )
            DeviceClimateStatus.CRITICAL -> DCSInfo(
                iconRes = iconRes,
                color = 0xFFF44336.toInt(), // Red
                title = "Align",
                description = "Battery overheating!",
                detailedDescription = "Battery temperature is critically high."
            )
            DeviceClimateStatus.UNKNOWN -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF9E9E9E.toInt(), // Gray (neutral)
                title = "No Green",
                description = "Monitoring device status",
                detailedDescription = "Monitoring device status..."
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleChargingStarted() {
        serviceScope.launch {
            try {
                delay(2000)
                
                val deviceId = DeviceUtils.getStoredDeviceId(applicationContext)
                val stationCode = WifiUtils.getHashedWiFiBSSID(applicationContext)
                WifiUtils.getWiFiSSID(applicationContext)
                
                val batteryIntent = applicationContext.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                
                var consumption: Double? = null
                var voltage: Double? = null
                var watt: Double? = null
                
                if (batteryIntent != null) {
                    val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    if (voltageMv > 0) {
                        voltage = voltageMv / 1000.0
                    }
                    
                    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    
                    if (voltage != null && voltage > 0) {
                        val estimatedCurrent = if (isCharging) 1.5 else 0.5
                        watt = voltage * estimatedCurrent
                        consumption = watt / 1000.0
                    }
                }

                if (deviceId != null && stationCode != null) {
                    sendStationInfo(deviceId, stationCode, consumption, voltage, watt)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling charging event: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "handleChargingEvent")
                    scope.setContexts("charging", mapOf(
                        "device_id" to (deviceId ?: "null"),
                        "station_code" to (currentStationCode ?: "null")
                    ))
                    Sentry.captureException(e)
                }
            }
        }
    }

    private suspend fun sendStationInfo(
        deviceId: String,
        stationCode: String,
        consumption: Double?,
        voltage: Double?,
        watt: Double?
    ) {
        try {
            val api = NetworkModule.api
            val request = SetStationRequest(
                deviceId = deviceId,
                stationCode = stationCode,
                consumption = consumption,
                voltage = voltage,
                watt = watt
            )

            val response = kotlinx.coroutines.withTimeoutOrNull(10000) {
                api.setStation(request)
            }

            if (response?.isSuccessful == true) {
                Log.d(TAG, "Successfully set station info for device $deviceId")
            } else {
                Log.w(TAG, "Failed to set station info. Status: ${response?.code() ?: "timeout"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Station info error: ${e.javaClass.simpleName}")
            Sentry.withScope { scope ->
                scope.setTag("operation", "sendStationInfo")
                scope.setContexts("station", mapOf(
                    "device_id" to deviceId,
                    "station_code" to stationCode
                ))
                Sentry.captureException(e)
            }
        }
    }

    private fun sendBackgroundData() {
        Log.i(TAG, "Charging stopped - would send background data here")
    }

    private fun getBatteryCapacity(): Double {
        val chargeCounter =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return if (chargeCounter == Int.MIN_VALUE || capacity == Int.MIN_VALUE) {
            0.0
        } else {
            (chargeCounter / capacity * 100).toDouble()
        }
    }

    private fun updateNotification() {
        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        serviceScope.launch {
            var currentAmperes = BatteryUtil.getBatteryCurrentNowInAmperes(currentNow)
            
            val isCharging = chargeState?.isCharging == true
            val hasCorrectSign = (isCharging && currentAmperes > 0) || (!isCharging && currentAmperes < 0)
            
            if (!hasCorrectSign && kotlin.math.abs(currentAmperes) > 0.01) {
                if (isCharging) {
                    currentAmperes = kotlin.math.abs(currentAmperes)
                } else {
                    currentAmperes = -kotlin.math.abs(currentAmperes)
                }
            }
            
            try {
                // Get DCS information
                val dcsInfo = getDeviceClimateStatusInfo()
//                Log.d(TAG, "Updating notification with DCS: ${currentDCS.name}, Color: ${dcsInfo.color}")
                
                // Build subtitle: "Charging" if charging, otherwise empty
                val subtitle = if (isCharging) {
                    "Charging"
                } else {
                    "" // Empty when not charging
                }
                
                val updatedNotificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(dcsInfo.iconRes)
                    .setColor(dcsInfo.color)
                    .setContentTitle(dcsInfo.title) // "Green Climate Status", "No Green", or "Align"
                    .setContentText(if (subtitle.isNotEmpty()) subtitle else null) // Just "Charging" or empty
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(contentIntent)
                    .setDefaults(0) // No sound/vibration on updates
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setShowWhen(false)
                    .clearActions()
            
                val builtNotification = updatedNotificationBuilder.build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    builtNotification.flags = builtNotification.flags or Notification.FLAG_NO_CLEAR
                }
                
                try {
//                    Log.d(TAG, "Updating foreground notification with ID: $NOTIFICATION_ID")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            builtNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, builtNotification)
                    }
//                    Log.d(TAG, "Notification updated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating foreground notification: ${e.message}", e)
                    e.printStackTrace()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        notificationManager.notify(NOTIFICATION_ID, builtNotification)
                    } else {
                        kotlinx.coroutines.delay(100)
                        try {
                            startForeground(
                                NOTIFICATION_ID,
                                builtNotification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            )
                        } catch (retryException: Exception) {
                            Log.e(TAG, "Retry startForeground also failed: ${retryException.message}", retryException)
                        }
                    }
                }
                
                notification = updatedNotificationBuilder
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateNotification: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun showStationCodeNotification() {
        val stationCode = currentStationCode

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
            .setContentText(if (stationCode != null && stationCode.isNotBlank()) {
                "Station Code: $stationCode"
            } else {
                "Verify Green Energy"
            })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (stationCode != null && stationCode.isNotBlank()) {
                    "Current Station: $stationCode"
                } else {
                    "Tap Enter Code to verify your charging session with a station code."
                }
            ))
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stationCodeUpdateReceiver)
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

    companion object {
        private const val CHANNEL_ID = "battery_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private const val STATION_CODE_CHANNEL_ID = "station_code_channel"
        private const val STATION_CODE_NOTIFICATION_ID = 2
        private const val STATION_CODE_GROUP_KEY = "com.example.thingsappandroid.station_code_group"
    }
}

/**
 * Device Climate Status levels
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
 * Data class for DCS information
 */
data class DCSInfo(
    val iconRes: Int,
    val color: Int,
    val title: String,
    val description: String,
    val detailedDescription: String
)

fun getAverage(list: List<Double>): Double {
    var sum = 0.0
    for (i in list) {
        sum += i
    }
    return if (list.isNotEmpty()) sum.div(list.size) else 0.0
}