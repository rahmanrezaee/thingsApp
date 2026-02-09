package com.example.thingsappandroid.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.utils.ClimateStatusManager
import com.example.thingsappandroid.services.utils.ConsumptionTracker
import com.example.thingsappandroid.services.utils.BatteryNotificationHelper
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that shows device climate status in the notification bar.
 * - On start: fetches device info if token exists (Splash handles register/getToken), or uses default when offline.
 * - Listens for WiFi/location: when both become available, runs getDeviceInfo once.
 * - Listens for battery: updates charging state, starts/stops consumption tracking, updates notification.
 * - Handles REQUEST_GET_DEVICE_INFO from HomeViewModel (e.g. after setStation or manual refresh).
 */
@AndroidEntryPoint
class BatteryService : Service() {

    // -------------------------------------------------------------------------
    // Injected dependencies (from ServiceModule / AppModule)
    // -------------------------------------------------------------------------

    @Inject
    lateinit var preferenceManager: PreferenceManager
    @Inject
    lateinit var tokenManager: TokenManager
    @Inject
    lateinit var thingsRepository: ThingsRepository
    @Inject
    lateinit var batteryManager: BatteryManager
    @Inject
    lateinit var notificationManager: NotificationManager
    @Inject
    lateinit var notificationHelper: BatteryNotificationHelper
    @Inject
    lateinit var climateStatusManager: ClimateStatusManager
    @Inject
    lateinit var consumptionTrackerFactory: ConsumptionTrackerFactory
    @Inject
    lateinit var deviceInfoApiFactory: DeviceInfoApiFactory

    // -------------------------------------------------------------------------
    // Runtime state (device ID, intents, connectivity, charging)
    // -------------------------------------------------------------------------

    private lateinit var deviceId: String
    private lateinit var contentIntent: PendingIntent
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = BatteryServiceNotificationIds

    private var chargeState: BatteryState? = null
    private var groupId: String? = null
    private var receiverRegistered = false
    private var currentDCS: DeviceClimateStatus = DeviceClimateStatus.UNKNOWN


    /** Created in onCreate from factories (need runtime deviceId). */
    private lateinit var consumptionTracker: ConsumptionTracker
    private lateinit var deviceInfoApi: BatteryServiceDeviceInfoApi
    private lateinit var notificationHandler: BatteryServiceNotificationHandler
    private lateinit var internalBroadcastReceiver: BroadcastReceiver

    // -------------------------------------------------------------------------
    // Broadcast handling: battery + WiFi/location → update state and notification
    // -------------------------------------------------------------------------

    private suspend fun runStopTracking(level: Int, voltage: Int) {
        consumptionTracker.stopTracking(level, voltage)
    }

    private fun handleConnectivityIntent(intent: Intent?) {
        // 1) If this is a battery intent, parse it and update charge state (and maybe start/stop consumption).
        if (BatteryServiceBroadcastHandler.isBatteryIntent(intent?.action)) {
            val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent!!, chargeState)
            val chargeStateChanged = result != null &&
                    (chargeState == null || chargeState?.isCharging != result.state.isCharging)

            if (chargeStateChanged) {
                if (!result.state.isCharging && result.wasCharging) {
                    notificationHandler.cancelStationCodeNotification()
                    notificationHandler.cancelLocationNotification()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val level = result.level
                        val voltage = result.voltage
                        serviceScope.launch {
                            runStopTracking(level, voltage)
                        }
                    }
                }
                groupId =
                    if (result.state.isCharging) java.util.UUID.randomUUID().toString() else null
                chargeState = result.state
                if (result.state.isCharging && !result.isInitialization) {
                    consumptionTracker.startTracking(
                        groupId!!,
                        result.state.level,
                        result.state.voltage
                    )
                    BatteryServiceConsumptionHandler.startPeriodicConsumptionReports(
                        serviceScope, { chargeState }, consumptionTracker
                    )
                }
                notificationHandler.updateNotification()
            }
        }


        serviceScope.launch {
            val wifiReady = BatteryServiceBroadcastHandler.isWiFiConnected(applicationContext)
            val locationReady = LocationUtils.hasLocationPermission(applicationContext) &&
                    LocationUtils.isLocationEnabled(applicationContext)
            val isPowerConnected =
                BatteryServiceBroadcastHandler.getChargingStatusFromIntent(intent)
                    ?: (chargeState?.isCharging == true)
            val currentBssid = WifiUtils.getHashedWiFiBSSID(applicationContext)
            val bssidChanged =
                currentBssid != null && currentBssid != preferenceManager.getLastWifiBssid()

            when {
                wifiReady && locationReady -> onWifiAndLocationReady(bssidChanged, isPowerConnected)
                wifiReady -> {

                    if (preferenceManager.getAppInForeground()) {
                        notificationHandler.cancelLocationNotification()
                    } else {
                        notificationHandler.showLocationRequiredNotification()
                    }
                }

                else -> {
                    notificationHandler.cancelLocationNotification()
                }
            }

            val shouldShowStationCode = wifiReady && locationReady &&
                    (isPowerConnected || (chargeState?.isCharging == true)) &&
                    notificationHandler.shouldShowStationCodeNotification(chargeState?.isCharging == true)
            if (shouldShowStationCode) {
                notificationHandler.maybeShowStationCodeNotificationOnce()
            }

            BatteryServiceConsumptionHandler.syncPendingConsumptions(consumptionTracker)

        }

    }

    private suspend fun onWifiAndLocationReady(
        bssidChanged: Boolean,
        isPowerConnected: Boolean,
    ) {

        notificationHandler.cancelLocationNotification()
        if (bssidChanged) {
            notificationHandler.cancelStationCodeNotification()
        }
        val shouldRunSetClimateStatus = isPowerConnected &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (shouldRunSetClimateStatus) {
            try {
                Log.d("getDeviceInfo", "ClimateStatusManager onWifiAndLocationReady")
                climateStatusManager.handleChargingStarted { reason, details, isPermissionDenied, isServicesDisabled ->
                    if (chargeState?.isCharging == true) {
                        notificationHandler.showLocationErrorNotification(
                            reason, details, isPermissionDenied, isServicesDisabled
                        )
                    }
                }
                notificationHandler.updateNotification()
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle: onCreate
    // -------------------------------------------------------------------------

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        try {
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: java.util.UUID.randomUUID().toString()

            consumptionTracker = consumptionTrackerFactory.create(deviceId, batteryManager)

            notificationHandler = BatteryServiceNotificationHandler(
                this,
                notificationManager,
                preferenceManager,
                notificationHelper,
                serviceScope,
                getContentIntent = { contentIntent },
                getCurrentDCS = { currentDCS },
                getStationCodeShownThisSession = { false },
                setStationCodeShownThisSession = {

                },
                getCurrentStationCode = { preferenceManager.getStationCode() }
            )

            deviceInfoApi = deviceInfoApiFactory.create(deviceId) {
                notificationHandler.updateNotification()
            }

            internalBroadcastReceiver = BatteryServiceBroadcastHandler.createReceiver(
                onDeviceInfoUpdated = { notificationHandler.updateNotification() },
                onForNewDeviceCallClimateStatus = {
                    // Reuse same debounced path so only one "online steps" run (and thus one SetClimateStatus)
                    handleConnectivityIntent(null)
                },
                onRequestGetDeviceInfo = {
                    serviceScope.launch { deviceInfoApi.fetchDeviceInfo() }
                },
                onBatteryIntent = { handleConnectivityIntent(it) },
                onConnectivityAction = { intent ->
                    serviceScope.launch {
                        delay(500)
                        handleConnectivityIntent(intent)
                    }
                }
            )

            notificationHandler.createChannels()
            fetchInitialBatteryState()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startNotificationMonitoring()
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            Toast.makeText(this, "ERROR: BatteryService - ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Lifecycle: onStartCommand — show notification, register receiver, run online/offline flow
    // -------------------------------------------------------------------------

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mainIntent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        )
        contentIntent = PendingIntent.getActivity(
            this, 1, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hasCached = preferenceManager.getLastDeviceInfo() != null
        val notif = try {
            notificationHandler.buildInitialNotification(hasCached)
        } catch (e: Exception) {
            NotificationCompat.Builder(this, ids.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Not green")
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notif.flags = notif.flags or Notification.FLAG_NO_CLEAR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ids.NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ids.NOTIFICATION_ID, notif)
        }

        if (!receiverRegistered) {
            BatteryServiceBroadcastHandler.registerReceiver(
                this, internalBroadcastReceiver, BatteryServiceBroadcastHandler.createIntentFilter()
            )
            receiverRegistered = true
        }

        serviceScope.launch {
            val wifiReady = BatteryServiceBroadcastHandler.isWiFiConnected(applicationContext)
            val locationReady = LocationUtils.hasLocationPermission(applicationContext) &&
                    LocationUtils.isLocationEnabled(applicationContext)

            if (wifiReady && locationReady) {
                deviceInfoApi.getDeviceInfoOnlineOrNoInternet()
            } else if (preferenceManager.getLastDeviceInfo() == null) {
                deviceInfoApi.applyDefaultDeviceInfo()
            } else {
                deviceInfoApi.sendDeviceInfoUpdatedBroadcast()
                notificationHandler.updateNotification()
            }
        }
        notificationHandler.updateNotification()

        if (chargeState?.isCharging == true && groupId != null) {
            consumptionTracker.startTracking(groupId!!, chargeState!!.level, chargeState!!.voltage)
            BatteryServiceConsumptionHandler.startPeriodicConsumptionReports(
                serviceScope, { chargeState }, consumptionTracker
            )
        }

        return START_STICKY
    }

    // -------------------------------------------------------------------------
    // Android 14+: re-show foreground notification if user dismisses it
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startNotificationMonitoring() {
        serviceScope.launch {
            var consecutiveMisses = 0
            while (true) {
                delay(2000)
                if (chargeState?.isCharging != true) {
                    consecutiveMisses = 0
                    continue
                }
                val ourNotificationVisible = notificationManager.activeNotifications
                    .any { it.id == ids.NOTIFICATION_ID }
                if (!ourNotificationVisible) {
                    consecutiveMisses++
                    if (consecutiveMisses >= 2) {
                        notificationHandler.recreateForegroundNotification()
                        consecutiveMisses = 0
                    }
                } else {
                    consecutiveMisses = 0
                }
            }
        }
    }

    /** Read current battery state from system (used on service start). */
    private fun fetchInitialBatteryState() {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return
            val result = BatteryServiceBatteryHandler.parseBatteryIntent(batteryIntent, null)
                ?: return
            chargeState = result.state
            if (result.state.isCharging) {
                groupId = java.util.UUID.randomUUID().toString()
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (receiverRegistered) {
                BatteryServiceBroadcastHandler.unregisterReceiver(this, internalBroadcastReceiver)
                receiverRegistered = false
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
        serviceScope.cancel()
    }
}
