package com.example.thingsappandroid.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import com.example.thingsappandroid.util.BatteryUtil
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.services.utils.BatteryConsumptionTracker
import com.example.thingsappandroid.services.utils.BatteryNotificationHelper
import com.example.thingsappandroid.services.utils.ClimateStatusManager
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.NetworkUtils
import com.example.thingsappandroid.util.WifiUtils
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext


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

    private var receiverRegistered = false
    private var currentDCS: DeviceClimateStatus = DeviceClimateStatus.UNKNOWN
    private var stationCodeShownThisSession = false

    /** Created in onCreate from factories (need runtime deviceId). */
    private lateinit var consumptionTracker: BatteryConsumptionTracker
    private lateinit var deviceInfoApi: BatteryServiceDeviceInfoApi
    private lateinit var notificationHandler: BatteryServiceNotificationHandler
    private lateinit var internalBroadcastReceiver: BroadcastReceiver


    private var chargeState: BatteryState? = null
    private var groupId: String? = null

    /** Debounce delay for WiFi/location intent handling to avoid duplicate requests. */
    private val wifiLocationDebounceMs = 400L
    private val wifiHandlerLock = Any()
    private var wifiHandlerJob: Job? = null

    // -------------------------------------------------------------------------
    // Charging helper — Service IS a Context, so call getSystemService directly
    // -------------------------------------------------------------------------

    private fun isCharging(): Boolean {
        // Trust our parsed state first (avoids BatteryManager delay),
        // fall back to system query if no state yet
        return chargeState?.isCharging
            ?: (getSystemService(BATTERY_SERVICE) as BatteryManager).isCharging
    }

    // -------------------------------------------------------------------------
    // Broadcast handling: battery + WiFi/location → update state and notification
    // -------------------------------------------------------------------------



    private suspend fun handleBatteryIntent(intent: Intent) {
        val action = intent.action
        Log.d("BatteryService", "handleBatteryIntent: action=$action")

        val isBatteryIntent = action == Intent.ACTION_BATTERY_CHANGED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == Intent.ACTION_POWER_DISCONNECTED
        if (!isBatteryIntent) {
            Log.d("BatteryService", "handleBatteryIntent: not a battery intent, ignoring")
            return
        }

        val result = BatteryServiceBatteryHandler.parseBatteryIntent(intent, chargeState)
        if (result == null) {
            Log.w("BatteryService", "handleBatteryIntent: parseBatteryIntent returned null")
            return
        }

        Log.d("BatteryService", "handleBatteryIntent: parsed → isCharging=${result.state.isCharging}, level=${result.level}, voltage=${result.voltage}, wasCharging=${result.wasCharging}, isInit=${result.isInitialization}")
        Log.d("BatteryService", "handleBatteryIntent: current chargeState isCharging=${chargeState?.isCharging}, new isCharging=${result.state.isCharging}")

        if (chargeState != null && chargeState?.isCharging == result.state.isCharging) {
            Log.d("BatteryService", "handleBatteryIntent: charging state unchanged, skipping")
            return
        }
        // startLoading
        chargeState = result.state

        if (!result.state.isCharging) {
            Log.d("BatteryService", "handleBatteryIntent: charger DISCONNECTED")
            notificationHandler.cancelStationCodeNotification()
            notificationHandler.cancelLocationNotification()
            stationCodeShownThisSession = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("BatteryService", "handleBatteryIntent: stopping consumption tracking level=${result.level} voltage=${result.voltage}")
                consumptionTracker.stopChargingSessionAndUpload()
            }
            groupId = null
            notificationHandler.updateNotification()    
            return
        }

        Log.d("BatteryService", "handleBatteryIntent: charger CONNECTED")
        if (!result.isInitialization) {
            runStartConsumptionIfChargingAndNotStarted()
        } else {
            Log.d("BatteryService", "handleBatteryIntent: skipping consumption tracking (initialization)")
        }

        Log.d("BatteryService", "handleBatteryIntent: calling runSetClimateStatusIfReady")
        runSetClimateStatusIfReady(null)
        // hide loading
       
    }

    /**
     * Start consumption tracking if the user is charging and it has not been started yet
     * (e.g. app started while already plugged in and first battery intent was initialization).
     */
    private  fun runStartConsumptionIfChargingAndNotStarted() {
        if (!isCharging() || groupId != null) return
        val state = BatteryServiceBatteryHandler.getCurrentBatteryState(applicationContext)
            ?: return
        if (!state.isCharging) return
        if (chargeState == null) chargeState = state
        groupId = java.util.UUID.randomUUID().toString()
        Log.d("BatteryService", "runStartConsumptionIfChargingAndNotStarted: starting consumption, groupId=$groupId")
        // Start 10s recording loop while charging.
        consumptionTracker.startChargingSessionIfNeeded()
    }

    private suspend fun runSetClimateStatusIfReady(climateStatusIntent: Intent?) {
        val hasInternet = NetworkUtils.isNetworkAvailable(applicationContext)
        val locationReady = LocationUtils.hasLocationPermission(applicationContext) &&
                LocationUtils.isLocationEnabled(applicationContext)
        val charging = isCharging()
        val apiLevel = Build.VERSION.SDK_INT
        val stationCode = climateStatusIntent?.getStringExtra(BatteryServiceActions.EXTRA_STATION_CODE)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isPowerSaveMode = pm.isPowerSaveMode

        Log.d("BatteryService", "runSetClimateStatusIfReady: hasInternet=$hasInternet, locationReady=$locationReady, isCharging=$charging, apiLevel=$apiLevel, isPowerSaveMode=$isPowerSaveMode, stationCode=${stationCode?.take(4)?.let { "$it***" } ?: "null"}")

        notificationHandler.cancelStationCodeNotification()
        stationCodeShownThisSession = false

        if (isPowerSaveMode) {
            Log.d("BatteryService", "runSetClimateStatusIfReady: Device is in Power Save / Ultra Low Battery mode. Keeping service alive but skipping intensive tasks.")
            // You could also call a notification update here to show "Eco mode active"
        }

        if (hasInternet && locationReady && charging && apiLevel >= Build.VERSION_CODES.R && !isPowerSaveMode) {
            try {
                Log.d("BatteryService", "runSetClimateStatusIfReady: calling climateStatusManager.handleChargingStarted")
                val climateStatus = climateStatusManager.handleChargingStarted(
                    job = currentCoroutineContext()[Job]!!,
                    stationCode = stationCode
                ) { reason, details, isPermissionDenied, isServicesDisabled ->
                    Log.w("BatteryService", "runSetClimateStatusIfReady: location error reason=$reason, details=$details, permDenied=$isPermissionDenied, servicesDisabled=$isServicesDisabled")
                    if (isCharging()) notificationHandler.showLocationErrorNotification(reason, details, isPermissionDenied, isServicesDisabled)
                }

                if (notificationHandler.shouldShowStationCodeNotification(isCharging())) {
                    Log.d("BatteryService", "handleBatteryIntent: WiFi+Location already on → maybeShowStationCodeNotificationOnce")
                    notificationHandler.maybeShowStationCodeNotificationOnce()
                }

                Log.d("BatteryService", "runSetClimateStatusIfReady: climateStatus result=$climateStatus")
            } catch (e: Exception) {
                Log.e("BatteryService", "runSetClimateStatusIfReady: error ${e.message}", e)
                Sentry.captureException(e)
            }
        } else {
            Log.d("BatteryService", "runSetClimateStatusIfReady: conditions not met, skipping SetClimateStatus")
            if (charging && !hasInternet) {
                Log.d("BatteryService", "runSetClimateStatusIfReady: charging without internet → applying offline charging device info")
                deviceInfoApi.applyOfflineChargingDeviceInfo()
            }
        }

        notificationHandler.updateNotification()
    }

    private fun handleWifiAndLocationIntent(intent: Intent?) {
        synchronized(wifiHandlerLock) {
            wifiHandlerJob?.cancel()
            wifiHandlerJob = serviceScope.launch {
            delay(wifiLocationDebounceMs)
            val isPowerConnected = isCharging()
            val currentBssid = WifiUtils.getHashedWiFiBSSID(applicationContext)
            val lastBssid = preferenceManager.getLastWifiBssid()
            val bssidChanged = currentBssid != null && currentBssid != lastBssid

            val wifiReady = BatteryServiceBroadcastHandler.isWiFiConnected(applicationContext)
            val locationReady = LocationUtils.hasLocationPermission(applicationContext) &&
                    LocationUtils.isLocationEnabled(applicationContext)

            Log.d("BatteryService", "handleWifiAndLocationIntent: wifiReady=$wifiReady, locationReady=$locationReady, isPowerConnected=$isPowerConnected, currentBssid=$currentBssid, lastBssid=$lastBssid, bssidChanged=$bssidChanged")

            when {
                wifiReady && locationReady -> {
                    Log.d("BatteryService", "handleWifiAndLocationIntent: WiFi+Location ready")

                    notificationHandler.cancelLocationNotification()

                    if (bssidChanged) {
                        Log.d("BatteryService", "handleWifiAndLocationIntent: BSSID changed → clearing station code, saving new BSSID")
                        notificationHandler.cancelStationCodeNotification()
                        stationCodeShownThisSession = false
                        preferenceManager.saveLastWifiBssid(currentBssid)
                    }

                    if (bssidChanged && isCharging()) {
                        Log.d("getDeviceInfo", "call bssidChanged → runSetClimateStatusIfReady")
                        runSetClimateStatusIfReady(null)
                    } else {
                        deviceInfoApi.fetchDeviceInfo()
                    }

                    notificationHandler.updateNotification()
                }

                wifiReady -> {
                    Log.d("BatteryService", "handleWifiAndLocationIntent: WiFi ready but NO location")
                    if (preferenceManager.getAppInForeground()) {
                        notificationHandler.cancelLocationNotification()
                    } else {
                        notificationHandler.showLocationRequiredNotification()
                    }
                }

                else -> {
                    Log.d("BatteryService", "handleWifiAndLocationIntent: NO WiFi")
                    notificationHandler.cancelLocationNotification()
                    if (isPowerConnected) {
                        Log.d("BatteryService", "handleWifiAndLocationIntent: charging offline → applying offline device info")
                        deviceInfoApi.applyOfflineChargingDeviceInfo()
                    }
                }
            }

            val shouldShowStationCode = wifiReady && locationReady &&
                    isPowerConnected &&
                    notificationHandler.shouldShowStationCodeNotification(isCharging())
            Log.d("BatteryService", "handleWifiAndLocationIntent: shouldShowStationCode=$shouldShowStationCode")
            if (shouldShowStationCode) {
                notificationHandler.maybeShowStationCodeNotificationOnce()
            }
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
            Log.d("BatteryService", "onCreate the service")


            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: java.util.UUID.randomUUID().toString()

            consumptionTracker = consumptionTrackerFactory.create(
                deviceId,
                batteryManager,
                serviceScope,
                { chargeState },
                { BatteryServiceBatteryHandler.getCurrentBatteryState(applicationContext) }
            )

            notificationHandler = BatteryServiceNotificationHandler(
                this,
                notificationManager,
                preferenceManager,
                notificationHelper,
                serviceScope,
                getContentIntent = { contentIntent },
                getCurrentDCS = { currentDCS },
                getStationCodeShownThisSession = { stationCodeShownThisSession },
                setStationCodeShownThisSession = { stationCodeShownThisSession = it },
                getCurrentStationCode = { preferenceManager.getStationCode() }
            )

            deviceInfoApi = deviceInfoApiFactory.create(deviceId, { notificationHandler.updateNotification() }, { isCharging() })

            val isIgnoring = BatteryUtil.isIgnoringBatteryOptimizations(this)
            Log.d("BatteryService", "onCreate: isIgnoringBatteryOptimizations=$isIgnoring")
            if (!isIgnoring) {
                Log.w("BatteryService", "onCreate: App is NOT ignoring battery optimizations. This may lead to service termination.")
            }

            Log.d("BatteryService", "onCreate the createReceiver")
            internalBroadcastReceiver = BatteryServiceBroadcastHandler.createReceiver(
                onDeviceInfoUpdated = { notificationHandler.updateNotification() },
                onForNewDeviceCallClimateStatus = { intent ->
                    serviceScope.launch {
                        Log.d("getDeviceInfo","call onForNewDeviceCallClimateStatus")
                        runSetClimateStatusIfReady(intent)
                    }
                },
                onBatteryIntent = {
                    serviceScope.launch {
                        handleBatteryIntent(it)
                    }
                },
                onRequestGetDeviceInfo = {
                    Log.d("getDeviceInfo","call onRequestGetDeviceInfo")
                    serviceScope.launch { deviceInfoApi.fetchDeviceInfo() }
                },

                onConnectivityAction = { intent ->
                    serviceScope.launch {
                        handleWifiAndLocationIntent(intent)
                    }
                }
            )
            Log.d("BatteryService", "onCreate the startNotificationMonitoring")
            notificationHandler.createChannels()
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val levelName = when (level) {
            TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            TRIM_MEMORY_MODERATE -> "MODERATE"
            TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "UNKNOWN($level)"
        }
        Log.w("BatteryService", "onTrimMemory: Level $levelName. System is under memory pressure!")
        
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            Log.w("BatteryService", "onTrimMemory: Critical pressure, reducing footprint.")
        }
    }

    override fun onDestroy() {
        Log.e("BatteryService", "onDestroy: Service is being destroyed!")
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("BatteryService", "onTaskRemoved: App swiped away, restarting service in 1s")
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            restartServicePendingIntent
        )
    }

    // -------------------------------------------------------------------------
    // Lifecycle: onStartCommand — show notification, register receiver, run online/offline flow
    // -------------------------------------------------------------------------

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        Log.d("BatteryService", "onStartCommand the Service")
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

        Log.d("BatteryService", "onStartCommand the runStartConsumptionIfChargingAndNotStarted")

        serviceScope.launch {
            // Start consumption if already charging (e.g. app updated while plugged in)
            runStartConsumptionIfChargingAndNotStarted()

            val wifiReady = BatteryServiceBroadcastHandler.isWiFiConnected(applicationContext)
            val locationReady = LocationUtils.hasLocationPermission(applicationContext) &&
                    LocationUtils.isLocationEnabled(applicationContext)

            if (wifiReady && locationReady) {
                deviceInfoApi.getDeviceInfoOnlineOrNoInternet()
            } else if (isCharging()) {
                deviceInfoApi.applyOfflineChargingDeviceInfo()
            } else if (preferenceManager.getLastDeviceInfo() == null) {
                deviceInfoApi.applyDefaultDeviceInfo()
            } else {
                deviceInfoApi.sendDeviceInfoUpdatedBroadcast()
                notificationHandler.updateNotification()
            }
        }
        notificationHandler.updateNotification()

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
                // Always monitor when the service should be running (STICKY)
                val ourNotificationVisible = notificationManager.activeNotifications
                    .any { it.id == ids.NOTIFICATION_ID }
                if (!ourNotificationVisible) {
                    consecutiveMisses++
                    if (consecutiveMisses >= 2) {
                        Log.d("BatteryService", "Notification missing, recreating...")
                        notificationHandler.recreateForegroundNotification()
                        consecutiveMisses = 0
                    }
                } else {
                    consecutiveMisses = 0
                }
            }
        }
    }
}
