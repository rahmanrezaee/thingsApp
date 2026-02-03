package com.example.thingsappandroid.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.dao.ConsumptionDao
import com.example.thingsappandroid.data.model.*
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.LocationUtils
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.sentry.Breadcrumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThingsRepository(
    private val consumptionDao: ConsumptionDao,
    private val preferenceManager: PreferenceManager
) {
    private val api = NetworkModule.api
    private val gson = Gson()
    private val deviceInfoMutex = Mutex()
    private val isSyncingDeviceInfo = java.util.concurrent.atomic.AtomicBoolean(false)

    private var authToken: String? = null

    fun getLastDeviceInfo(): DeviceInfoResponse? = preferenceManager.getLastDeviceInfo()

    fun saveLastDeviceInfo(deviceInfo: DeviceInfoResponse) {
        preferenceManager.saveLastDeviceInfo(deviceInfo)
    }
    
    fun createDefaultDeviceInfo(deviceId: String): DeviceInfoResponse {
        Log.d("ThingsRepo", "Creating default device info for offline mode")
        return DeviceInfoResponse(
            deviceId = deviceId,
            alias = Build.MODEL,
            commencementDate = null,
            thingId = null,
            climateStatus = 8,
            totalAvoided = 500.0,
            totalEmissions = 0.0,
            totalConsumed = 0.0,
            totalBudget = 500.0,
            remainingBudget = 500.0,
            userId = null,
            stationInfo = null,
            organizationInfo = null,
            carbonInfo = CarbonIntensityInfo(
                currentIntensity = 485.0,
                source = "Default",
                retrievedAt = null
            ),
            versionInfo = null
        )
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getDeviceSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serial = Build.getSerial()
                if (serial == Build.UNKNOWN) "SN-${Build.MODEL}" else serial
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: SecurityException) {
            "SN-${Build.BOARD}"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    suspend fun initializeDevice(
        context: Context,
        deviceId: String,
        stationCode: String? = null
    ): Triple<Boolean, String?, DeviceInfoResponse?> {
        return withContext(Dispatchers.IO) {
            try {
                val serialNumber = getDeviceSerialNumber()
                val wifiAddress = WifiUtils.getHashedWiFiBSSID(context)
                val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
                
                val registerResponse = api.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = deviceId,
                        name = "Android Device",
                        make = Build.MANUFACTURER,
                        model = Build.MODEL,
                        os = "Android ${Build.VERSION.RELEASE}",
                        category = "Mobile",
                        serialNumber = serialNumber,
                        wifiAddress = wifiAddress,
                        latitude = latitude,
                        longitude = longitude
                    )
                )
                
                if (!registerResponse.isSuccessful && registerResponse.code() != 400 && registerResponse.code() != 409) {
                    return@withContext Triple(false, null, null)
                }
                
                val tokenResponse = api.getToken(mapOf("DeviceId" to deviceId))
                if (!tokenResponse.isSuccessful) return@withContext Triple(false, null, null)
                
                val token = tokenResponse.body()?.token ?: return@withContext Triple(false, null, null)
                authToken = token
                NetworkModule.setAuthToken(token)
                
                val deviceInfo = syncDeviceInfo(context, deviceId, stationCode, skipRegister = true)
                Triple(true, token, deviceInfo)
            } catch (e: Exception) {
                Sentry.captureException(e)
                Triple(false, null, null)
            }
        }
    }

    suspend fun authenticate(deviceId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getToken(mapOf("DeviceId" to deviceId))
                if (response.isSuccessful) {
                    authToken = response.body()?.token
                    NetworkModule.setAuthToken(authToken)
                    authToken
                } else null
            } catch (e: Exception) {
                Sentry.captureException(e)
                null
            }
        }
    }

    suspend fun syncDeviceInfo(context: Context, deviceId: String, stationCode: String?, skipRegister: Boolean = false): DeviceInfoResponse? {
        if (!isSyncingDeviceInfo.compareAndSet(false, true)) return getLastDeviceInfo()
        
        return try {
            deviceInfoMutex.withLock {
                withContext(Dispatchers.IO) {
                    if (!skipRegister) {
                        api.registerDevice(
                            RegisterDeviceRequest(
                                deviceId = deviceId,
                                name = Build.MODEL ?: "Android Device",
                                make = Build.MANUFACTURER,
                                os = "Android ${Build.VERSION.RELEASE}",
                                category = "Mobile",
                                serialNumber = getDeviceSerialNumber()
                            )
                        )
                    }

                    val wifiAddress = WifiUtils.getHashedWiFiBSSID(context)
                    if (wifiAddress.isNullOrBlank()) {
                        Log.d("ThingsRepo", "WiFi address missing - not sending getDeviceInfo request")
                        return@withContext getLastDeviceInfo()
                    }
                    val (latitude, longitude) = LocationUtils.getLocationCoordinates(context) ?: Pair(0.0, 0.0)
                    val request = DeviceInfoRequest(deviceId, stationCode, wifiAddress, null, latitude, longitude)
                    val infoResponse = api.getDeviceInfo(request)

                    if (infoResponse.isSuccessful) {
                        infoResponse.body()?.data?.also { saveLastDeviceInfo(it) } ?: getLastDeviceInfo()
                    } else getLastDeviceInfo()
                }
            }
        } finally {
            isSyncingDeviceInfo.set(false)
        }
    }

    suspend fun updateAlias(deviceId: String, newAlias: String): Boolean {
        return try {
            api.setDeviceAlias(SetDeviceAliasRequest(deviceId, newAlias)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun authorizeGreenLogin(deviceId: String, sessionId: String, requestedBy: String, requestedUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = GreenLoginAuthRequest(deviceId, sessionId, Build.MANUFACTURER, Build.MODEL, requestedBy, requestedUrl)
                val response = api.greenLoginAuth(request)
                response.code() == 200
            } catch (e: Exception) {
                if (e is com.google.gson.JsonSyntaxException && (e.message?.contains("BEGIN_ARRAY") == true)) true
                else false
            }
        }
    }

}
