package com.example.thingsappandroid.data.repository

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.thingsappandroid.data.model.*
import com.example.thingsappandroid.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class ThingsRepository {
    private val api = NetworkModule.api

    // In a real app, store this securely (DataStore/EncryptedSharedPreferences)
    private var authToken: String? = null

    private fun createDefaultDeviceInfoResponse(deviceId: String): DeviceInfoResponse {
        return DeviceInfoResponse(
            deviceId = deviceId,
            alias = "My Device",
            commencementDate = null,
            thingId = null,
            climateStatus = null,
            totalAvoided = 0.0,
            totalEmissions = 0.0,
            totalConsumed = 0.0,
            totalBudget = 0.0,
            remainingBudget = 0.0,
            userId = null,
            stationInfo = null,
            organizationInfo = null,
            carbonInfo = null,
            versionInfo = null
        )
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getDeviceSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Build.getSerial() requires READ_PHONE_STATE. 
                // On Android 10+ (API 29), it returns Build.UNKNOWN for non-system apps.
                val serial = Build.getSerial()
                if (serial == Build.UNKNOWN) "SN-${Build.MODEL}" else serial
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: SecurityException) {
            Log.w("ThingsRepo", "Permission denied for hardware serial: ${e.message}")
            "SN-${Build.BOARD}" // Fallback identifier
        } catch (e: Exception) {
            Log.w("ThingsRepo", "Error getting serial: ${e.message}")
            "Unknown"
        }
    }

    suspend fun authenticate(deviceId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getToken(mapOf("DeviceId" to deviceId))
                if (response.isSuccessful) {
                    authToken = response.body()?.token
                    NetworkModule.setAuthToken(authToken)
                    Log.d("ThingsRepo", "Authentication Successful (Status: ${response.code()}). Token: ${authToken?.take(10)}...")
                    authToken
                } else {
                    Log.e("ThingsRepo", "Authentication Failed. Status: ${response.code()}, Error: ${response.errorBody()?.string()}")
                    null
                }
            } catch (e: Exception) {
                // ERROR ANALYSIS:
                // 1. UnknownHostException -> DNS/Internet issue.
                // 2. ConnectException -> Server down or port closed.
                // 3. HttpException (400-500) -> Server rejected request (check body/headers).
                // 4. MalformedJsonException -> Response didn't match data class.
                Log.e("ThingsRepo", "CRITICAL: Auth failed. Cause: ${e.javaClass.simpleName}, Msg: ${e.message}", e)
                null
            }
        }
    }

    suspend fun syncDeviceInfo(deviceId: String): DeviceInfoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Register Device
                val serialNumber = getDeviceSerialNumber()
                val registerResponse = api.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = deviceId,
                        name = "Android Device",
                        make = android.os.Build.MANUFACTURER,
                        os = "Android " + android.os.Build.VERSION.RELEASE,
                        category = "Mobile",
                        serialNumber = serialNumber
                    )
                )

                if (!registerResponse.isSuccessful) {
                    Log.w("ThingsRepo", "Registration failed: ${registerResponse.code()}. Trying to fetch info anyway.")
                }

                // 2. Fetch Device Info (Full sync)
                val infoResponse = api.getDeviceInfo(DeviceInfoRequest(deviceId = deviceId))
                
                if (infoResponse.isSuccessful && infoResponse.body() != null) {
                    infoResponse.body()
                } else {
                    Log.w("ThingsRepo", "Get Device Info failed: ${infoResponse.code()}. Using default.")
                    createDefaultDeviceInfoResponse(deviceId)
                }
            } catch (e: Exception) {
                Log.e("ThingsRepo", "Sync Info failed: ${e.message}", e)
                null
            }
        }
    }

    suspend fun updateAlias(deviceId: String, newAlias: String): Boolean {
        return try {
            val response = api.setDeviceAlias(SetDeviceAliasRequest(deviceId, newAlias))
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getCarbonIntensity(lat: Double, lon: Double): LatestIntensityResponse? {
        return try {
            api.getLatestIntensity(GetLatestIntensityRequest(lat, lon)).body()
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun uploadConsumption(
        deviceId: String,
        watts: Double,
        kwh: Double,
        batteryLevel: Int,
        isCharging: Boolean
    ) {
        withContext(Dispatchers.IO) {
            try {
                val now = Instant.now()
                val request = AndroidMeasurementRequest(
                    AndroidMeasurementModel(
                        deviceId = deviceId,
                        totalWatts = watts,
                        totalWattHours = kwh,
                        totalGramsCO2 = kwh * 400, // Estimation if not provided by server
                        from = now.minusSeconds(60).toString(),
                        to = now.toString(),
                        batteryLevelFrom = batteryLevel,
                        batteryLevelTo = batteryLevel,
                        isCharging = isCharging,
                        // New fields defaults
                        sourceType = "Battery",
                        batteryCapacity = 5000.0, // Example hardcode or fetch from power manager
                        emissionFactor = 0.0
                    )
                )
                val response = api.addDeviceConsumption(request)
                if (response.isSuccessful) {
                    Log.d("ThingsRepo", "Consumption upload successful (Status: ${response.code()})")
                } else {
                    Log.d("ThingsRepo", "Consumption upload failed (Status: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.d("ThingsRepo", "Upload failed: ${e.message}")
            }
        }
    }
}