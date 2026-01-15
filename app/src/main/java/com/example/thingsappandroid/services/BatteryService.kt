package com.example.thingsappandroid.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.R
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.model.SetStationRequest
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.util.DeviceUtils
import com.example.thingsappandroid.util.TimeUtility
import com.example.thingsappandroid.util.WifiUtils
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
 * Based on the provided pattern from umweltify project.
 */
class BatteryService : Service() {

    private val TAG = "BatteryService"
    private lateinit var deviceId: String
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private var intervalRate: Int = 1 // seconds - update every second
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var chargeState: BatteryState? = null
    private var groupId: String? = null

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        context = this
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        // Create notification channel
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentMain = Intent(this, MainActivity::class.java)
        intentMain.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            1, intentMain,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ThingsApp is running...")
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setDefaults(Notification.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setWhen(System.currentTimeMillis())

        startForeground(NOTIFICATION_ID, notification.build())
        showChargingOptionNotification()
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows battery monitoring status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showChargingOptionNotification() {
        // Observe battery state changes
        BatteryReceiver.observe(this)
            .onEach { batteryState ->
                Log.i(
                    TAG,
                    "ChargingState ${chargeState?.isCharging} ${batteryState.isCharging}"
                )
                
                if (chargeState?.isCharging == null || chargeState?.isCharging != batteryState.isCharging) {
                    Log.i(TAG, "ShowNotification ${chargeState?.isCharging}")

                    if (chargeState?.isCharging != null && !batteryState.isCharging) {
                        // Charging stopped - send background data
                        sendBackgroundData()
                    }

                    if (chargeState?.isCharging != null && batteryState.isCharging) {
                        // Charging started - handle station detection
                        handleChargingStarted()
                    }

                    chargeState = batteryState
                    groupId = UUID.randomUUID().toString()
                    updateNotification()
                }
            }
            .launchIn(serviceScope)

        // Periodic notification updates - every second
        serviceScope.launch {
            while (true) {
                delay(intervalRate * 1000L) // 1 second
                updateNotification() // Update regardless of chargeState to show current info
            }
        }
    }

    private fun handleChargingStarted() {
        serviceScope.launch {
            try {
                delay(2000) // Small delay for stability
                
                val deviceId = DeviceUtils.getStoredDeviceId(applicationContext)
                val stationCode = WifiUtils.getHashedWiFiBSSID(applicationContext)
                WifiUtils.getWiFiSSID(applicationContext) // Log the WiFi SSID
                
                // Get battery information
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
                        val estimatedCurrent = if (isCharging) 1.5 else 0.5 // Amperes
                        watt = voltage * estimatedCurrent
                        consumption = watt / 1000.0
                    }
                }

                if (deviceId != null && stationCode != null) {
                    Log.d(TAG, "Device ID: $deviceId, Station Code: $stationCode, Consumption: $consumption, Voltage: $voltage, Watt: $watt")
                    sendStationInfo(deviceId, stationCode, consumption, voltage, watt)
                } else {
                    Log.w(TAG, "Could not get device ID or station code. DeviceId: $deviceId, StationCode: $stationCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling charging event: ${e.message}", e)
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
        }
    }

    private fun sendBackgroundData() {
        // This would send accumulated battery data when charging stops
        // For now, we'll log it - can be extended to send to server
        Log.i(TAG, "Charging stopped - would send background data here")
        
        // In the original code, this would:
        // 1. Get battery data from repository
        // 2. Calculate averages
        // 3. Send to server
        // Since we don't have a repository, we'll skip this for now
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
            // Get current in amperes using BatteryUtil which handles:
            // - Correct conversion from microamperes to amperes (divide by 1,000,000)
            // - Xiaomi device sign inversion
            var currentAmperes = BatteryUtil.getBatteryCurrentNowInAmperes(currentNow)
            
            // Verify sign matches charging status as a safety check
            // (BatteryUtil should already handle Xiaomi, but we verify for other edge cases)
            val isCharging = chargeState?.isCharging == true
            val hasCorrectSign = (isCharging && currentAmperes > 0) || (!isCharging && currentAmperes < 0)
            
            if (!hasCorrectSign && kotlin.math.abs(currentAmperes) > 0.01) {
                // Sign mismatch detected - device may be reporting incorrectly
                // Adjust sign to match charging status
                if (isCharging) {
                    // Should be positive when charging
                    currentAmperes = kotlin.math.abs(currentAmperes)
                } else {
                    // Should be negative when discharging
                    currentAmperes = -kotlin.math.abs(currentAmperes)
                }
                Log.w(TAG, "Current sign mismatch detected - adjusted to match charging status")
            }
            
            // Format current with 2 decimal places
            val currentText = String.format("%.2f A", currentAmperes)
            
            // Build notification text with current and charging status only
            val statusText = getBatteryStatusText()
            val notificationText = "Current: $currentText | $statusText"
            
            val updatedNotification = notification
                .setContentText(notificationText)

            notificationManager.notify(NOTIFICATION_ID, updatedNotification.build())

            Log.i(
                TAG,
                "Battery update - Current: $currentText, Status: $statusText"
            )
        }
    }

    private fun getBatteryStatusText(): String {
        return when {
            chargeState?.isCharging == true -> "Charging"
            chargeState?.isCharging == false -> "Not Charging"
            else -> "Unknown"
        }
    }

    private fun iconFor(percent: Int): Int {
        // Return appropriate icon based on battery level
        // For now, use system icon
        return android.R.drawable.ic_dialog_info
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "battery_monitoring_channel"
        private const val NOTIFICATION_ID = 1
    }
}

fun getAverage(list: List<Double>): Double {
    var sum = 0.0
    for (i in list) {
        sum += i
    }
    return if (list.isNotEmpty()) sum.div(list.size) else 0.0
}
