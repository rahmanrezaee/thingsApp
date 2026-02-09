package com.example.thingsappandroid.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.R
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.services.utils.BatteryNotificationHelper
import com.example.thingsappandroid.util.ClimateUtils
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * All notification building and display for BatteryService. Uses BatteryServiceNotificationIds.
 */
class BatteryServiceNotificationHandler(
    private val service: Service,
    private val notificationManager: NotificationManager,
    private val preferenceManager: PreferenceManager,
    private val notificationHelper: BatteryNotificationHelper,
    private val scope: CoroutineScope,
    private val getContentIntent: () -> PendingIntent,
    private val getCurrentDCS: () -> DeviceClimateStatus,
    private val getStationCodeShownThisSession: () -> Boolean,
    private val setStationCodeShownThisSession: (Boolean) -> Unit,
    private val getCurrentStationCode: () -> String?
) {
    private val ids = BatteryServiceNotificationIds

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            listOf(
                NotificationChannel(ids.CHANNEL_ID, "Device Climate Status", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shows current device climate status"
                    setShowBadge(true); enableVibration(false); enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setBypassDnd(false)
                },
                NotificationChannel(ids.LOCATION_PERMISSION_CHANNEL_ID, "Location Permission", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifies when location permission is needed to verify charging station"
                    setShowBadge(true); enableVibration(true); enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(ids.STATION_CODE_CHANNEL_ID, "Station Code", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Station code input and display"
                    setShowBadge(true); enableVibration(true); enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            ).forEach { notificationManager.createNotificationChannel(it) }
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    fun buildInitialNotification(hasCachedDeviceInfo: Boolean): Notification {
        val dcsInfo = notificationHelper.getDCSInfo(getCurrentDCS())
        val (title, color, iconRes) = if (hasCachedDeviceInfo) {
            val status = preferenceManager.getLastDeviceInfo()?.climateStatus
            if (status != null) {
                val data = ClimateUtils.getMappedClimateData(status)
                Triple(data.title, when (status) {
                    8 -> 0xFFFF9800.toInt(); 5, 6, 7, 9 -> 0xFF4CAF50.toInt()
                    else -> 0xFF9E9E9E.toInt()
                }, dcsInfo.iconRes)
            } else Triple(dcsInfo.title, dcsInfo.color, dcsInfo.iconRes)
        } else Triple("Checking climate status...", 0xFF9E9E9E.toInt(), dcsInfo.iconRes)
        val builder = NotificationCompat.Builder(service, ids.CHANNEL_ID)
            .setSmallIcon(iconRes).setColor(color).setContentTitle(title)
            .setAutoCancel(false).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(getContentIntent()).setDefaults(Notification.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_SERVICE).setShowWhen(false)
        var notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        }
        return notification
    }

    fun updateNotification() {
        scope.launch {
            try {
                val cachedDeviceInfo = preferenceManager.getLastDeviceInfo()
                val apiClimateStatus = cachedDeviceInfo?.climateStatus
                val hasStation = preferenceManager.getHasStation()
                val isGreen = apiClimateStatus != null && apiClimateStatus in API_GREEN_CLIMATE_STATUSES
                if (hasStation || isGreen) {
                    notificationManager.cancel(ids.STATION_CODE_NOTIFICATION_ID)
                    setStationCodeShownThisSession(false)
                }
                val (title, colorInt, iconRes) = if (apiClimateStatus != null) {
                    val data = ClimateUtils.getMappedClimateData(apiClimateStatus)
                    Triple(data.title, when (apiClimateStatus) {
                        8 -> 0xFFFF9800.toInt(); 5, 6, 7, 9 -> 0xFF4CAF50.toInt()
                        else -> 0xFF9E9E9E.toInt()
                    }, try { service.resources.getDrawable(R.drawable.ic_climate_status, null); R.drawable.ic_climate_status } catch (e: Exception) { android.R.drawable.ic_dialog_info })
                } else {
                    val iconRes = try { service.resources.getDrawable(R.drawable.ic_climate_status, null); R.drawable.ic_climate_status } catch (e: Exception) { android.R.drawable.ic_dialog_info }
                    Triple("Checking climate status...", 0xFF9E9E9E.toInt(), iconRes)
                }
                val builder = NotificationCompat.Builder(service, ids.CHANNEL_ID)
                    .setSmallIcon(iconRes).setColor(colorInt).setContentTitle(title)
                    .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
                    .setContentIntent(getContentIntent()).setDefaults(0)
                    .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_SERVICE).setShowWhen(false).clearActions()
                var built = builder.build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) built.flags = built.flags or Notification.FLAG_NO_CLEAR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    service.startForeground(ids.NOTIFICATION_ID, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    service.startForeground(ids.NOTIFICATION_ID, built)
                }
            } catch (e: Exception) { Sentry.captureException(e) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun recreateForegroundNotification() {
        try {
            val apiClimateStatus = preferenceManager.getLastDeviceInfo()?.climateStatus
            val (title, colorInt, iconRes) = if (apiClimateStatus != null) {
                val data = ClimateUtils.getMappedClimateData(apiClimateStatus)
                val color = when (apiClimateStatus) { 8 -> 0xFFFF9800.toInt(); 5, 6, 7, 9 -> 0xFF4CAF50.toInt(); else -> 0xFF9E9E9E.toInt() }
                Triple(data.title, color, try { R.drawable.ic_climate_status } catch (e: Exception) { android.R.drawable.ic_dialog_info })
            } else {
                val dcsInfo = notificationHelper.getDCSInfo(getCurrentDCS())
                Triple(dcsInfo.title, dcsInfo.color, dcsInfo.iconRes)
            }
            val builder = NotificationCompat.Builder(service, ids.CHANNEL_ID)
                .setSmallIcon(iconRes).setColor(colorInt).setContentTitle(title)
                .setAutoCancel(false).setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(getContentIntent()).setDefaults(0)
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_SERVICE).setShowWhen(false).clearActions()
            val built = builder.build()
            @Suppress("DEPRECATION")
            built.flags = built.flags or Notification.FLAG_NO_CLEAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(ids.NOTIFICATION_ID, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                service.startForeground(ids.NOTIFICATION_ID, built)
            }
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    fun showLocationRequiredNotification() {
        try {
            val pendingIntent = PendingIntent.getActivity(service, ids.CHARGING_STARTED_LOCATION_REQUEST_CODE,
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(NotificationChannel(ids.LOCATION_PERMISSION_CHANNEL_ID, "Location Permission", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Enable location for carbon tracking"; setShowBadge(true); enableVibration(true); enableLights(true)
                })
            }
            notificationManager.notify(ids.CHARGING_STARTED_LOCATION_NOTIFICATION_ID,
                NotificationCompat.Builder(service, ids.LOCATION_PERMISSION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_power).setContentTitle("Enable Location").setContentText("Enable location for carbon tracking")
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pendingIntent).build())
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    fun showStationCodeNotification() {
        if (preferenceManager.getHasStation()) return
        val intentMain = Intent(service, MainActivity::class.java).apply {
            action = BatteryServiceActions.OPEN_STATION_CODE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_station_code_dialog", true)
        }
        val contentIntent = PendingIntent.getActivity(service, 2, intentMain, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val code = getCurrentStationCode().orEmpty()
        notificationManager.notify(ids.STATION_CODE_NOTIFICATION_ID,
            NotificationCompat.Builder(service, ids.STATION_CODE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Station Code")
                .setContentText(if (code.isBlank()) "Verify Green Energy" else "Station Code: $code")
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (code.isBlank()) "Tap Enter Code to verify your charging session with a station code." else "Current Station: $code"))
                .setPriority(NotificationCompat.PRIORITY_MAX).setDefaults(Notification.DEFAULT_ALL).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE).setGroup(ids.STATION_CODE_GROUP_KEY).setAutoCancel(true).setOngoing(false)
                .setContentIntent(contentIntent).addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_input_add, "Enter Code", contentIntent).build()).build())
    }

    fun showLocationErrorNotification(errorReason: String?, errorDetails: String?, isPermissionDenied: Boolean, isServicesDisabled: Boolean) {
        try {
            val settingsIntent = when {
                isServicesDisabled -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                isPermissionDenied -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", service.packageName, null) }
                else -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
            val pendingIntent = PendingIntent.getActivity(service, ids.LOCATION_ERROR_REQUEST_CODE, settingsIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(NotificationChannel(ids.LOCATION_ERROR_CHANNEL_ID, "Location Error", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications for location errors during charging"; enableVibration(true); enableLights(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                })
            }
            val title = if (isServicesDisabled) "Enable Location Services" else "Location Permission Required"
            val content = errorDetails ?: "Please enable location for carbon tracking"
            notificationManager.notify(ids.LOCATION_ERROR_NOTIFICATION_ID,
                NotificationCompat.Builder(service, ids.LOCATION_ERROR_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_power).setContentTitle(title).setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setAutoCancel(true).setOngoing(false).setContentIntent(pendingIntent)
                    .addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_preferences, "Open Settings", pendingIntent).build()).build())
        } catch (e: Exception) {
            Sentry.withScope { scope -> scope.setTag("operation", "showLocationErrorNotification"); Sentry.captureException(e) }
        }
    }

    fun shouldShowStationCodeNotification(isCharging: Boolean): Boolean {
        if (!isCharging || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (getStationCodeShownThisSession()) return false
        if (preferenceManager.getHasStation()) return false
        val status = preferenceManager.getLastDeviceInfo()?.climateStatus
        if (status != null && status in API_GREEN_CLIMATE_STATUSES) return false
        return true
    }

    fun maybeShowStationCodeNotificationOnce(){
        scope.launch {
            delay(3000)
            showStationCodeNotification()
        }
    }

    fun cancelLocationNotification() = notificationManager.cancel(ids.CHARGING_STARTED_LOCATION_NOTIFICATION_ID)
    fun cancelStationCodeNotification() = notificationManager.cancel(ids.STATION_CODE_NOTIFICATION_ID)
}
