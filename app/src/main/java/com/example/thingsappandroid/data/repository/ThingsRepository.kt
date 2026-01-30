package com.example.thingsappandroid.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.google.gson.Gson
import com.example.thingsappandroid.data.model.*
import com.example.thingsappandroid.data.remote.NetworkModule
import com.example.thingsappandroid.util.BatteryUtil
import com.example.thingsappandroid.util.WifiUtils
import io.sentry.Sentry
import io.sentry.Breadcrumb
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

    /**
     * Complete device initialization: Register device, get token, and sync device info.
     * This method handles all initialization requests in the correct order.
     * 
     * @param context Application context
     * @param deviceId The device ID to register and authenticate
     * @param stationCode Optional station code
     * @return Triple of (success: Boolean, token: String?, deviceInfo: DeviceInfoResponse?)
     */
    suspend fun initializeDevice(
        context: Context,
        deviceId: String,
        stationCode: String? = null
    ): Triple<Boolean, String?, DeviceInfoResponse?> {
        return withContext(Dispatchers.IO) {
            try {
                // STEP 1: Register device first (POST /v4/thingsapp/registerdevice)
                Log.d("ThingsRepo", "STEP 1: Registering device with deviceId: $deviceId")
                val serialNumber = getDeviceSerialNumber()
                
                val registerResponse = api.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = deviceId,
                        name = "Android Device",
                        make = Build.MANUFACTURER,
                        os = "Android ${Build.VERSION.RELEASE}",
                        category = "Mobile",
                        serialNumber = serialNumber
                    )
                )
                
                val registerStatusCode = registerResponse.code()
                if (registerResponse.isSuccessful) {
                    // API returns [] (empty array) on success - we don't need to parse it
                    Log.d("ThingsRepo", "✓ Device registered successfully (Status: $registerStatusCode)")
                } else {
                    val errorBody = registerResponse.errorBody()?.string()
                    Log.w("ThingsRepo", "Device registration returned $registerStatusCode: $errorBody")
                    
                    // If device already exists (400, 409), that's okay - continue to authentication
                    if (registerStatusCode == 400 || registerStatusCode == 409) {
                        Log.d("ThingsRepo", "Device already exists on server, continuing to authentication")
                    } else {
                        // For other errors, fail
                        Log.e("ThingsRepo", "Device registration failed with status $registerStatusCode, cannot proceed")
                        return@withContext Triple(false, null, null)
                    }
                }
                
                // STEP 2: Get token after device is registered (POST /v4/thingsapp/GetTokenForThingsApp)
                Log.d("ThingsRepo", "STEP 2: Authenticating to get token for deviceId: $deviceId")
                val breadcrumb = Breadcrumb("Starting authentication")
                breadcrumb.category = "auth"
                breadcrumb.level = io.sentry.SentryLevel.INFO
                breadcrumb.setData("device_id", deviceId)
                Sentry.addBreadcrumb(breadcrumb)
                
                val tokenResponse = api.getToken(mapOf("DeviceId" to deviceId))
                if (!tokenResponse.isSuccessful) {
                    Log.e("ThingsRepo", "Authentication Failed. Status: ${tokenResponse.code()}, Error: ${tokenResponse.errorBody()?.string()}")
                    return@withContext Triple(false, null, null)
                }
                
                val token = tokenResponse.body()?.token
                if (token == null) {
                    Log.e("ThingsRepo", "Authentication succeeded but no token in response")
                    return@withContext Triple(false, null, null)
                }
                
                authToken = token
                NetworkModule.setAuthToken(token)
                
                val successBreadcrumb = Breadcrumb("Authentication successful")
                successBreadcrumb.category = "auth"
                successBreadcrumb.level = io.sentry.SentryLevel.INFO
                Sentry.addBreadcrumb(successBreadcrumb)
                
                Log.d("ThingsRepo", "✓ Authentication Successful (Status: ${tokenResponse.code()}). Token: ${token.take(10)}...")
                
                // STEP 3: Sync device info to get full device information (POST /v4/thingsapp/getdeviceinfo)
                Log.d("ThingsRepo", "STEP 3: Syncing device info for deviceId: $deviceId")
                val deviceInfo = syncDeviceInfo(context, deviceId, stationCode)
                
                Log.d("ThingsRepo", "✓ Device initialization completed successfully")
                Triple(true, token, deviceInfo)
                
            } catch (e: Exception) {
                Log.e("ThingsRepo", "CRITICAL: initializeDevice failed. Cause: ${e.javaClass.simpleName}, Msg: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "initializeDevice")
                    scope.setTag("device_id", deviceId)
                    scope.setContexts("auth", mapOf("device_id" to deviceId))
                    Sentry.captureException(e)
                }
                Triple(false, null, null)
            }
        }
    }

    /**
     * Register device and get token in one method.
     * This ensures device is registered before authentication.
     * 
     * @param deviceId The device ID to register and authenticate
     * @return The authentication token, or null if registration or authentication failed
     */
    suspend fun registerDeviceAndGetToken(deviceId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // STEP 1: Register device first (POST /v4/thingsapp/registerdevice)
                Log.d("ThingsRepo", "STEP 1: Registering device with deviceId: $deviceId")
                val serialNumber = getDeviceSerialNumber()
                
                val registerResponse = api.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = deviceId,
                        name = "Android Device",
                        make = Build.MANUFACTURER,
                        os = "Android ${Build.VERSION.RELEASE}",
                        category = "Mobile",
                        serialNumber = serialNumber
                    )
                )
                
                val registerStatusCode = registerResponse.code()
                if (registerResponse.isSuccessful) {
                    // API returns [] (empty array) on success - we don't need to parse it
                    Log.d("ThingsRepo", "✓ Device registered successfully (Status: $registerStatusCode)")
                } else {
                    val errorBody = registerResponse.errorBody()?.string()
                    Log.w("ThingsRepo", "Device registration returned $registerStatusCode: $errorBody")
                    
                    // If device already exists (400, 409), that's okay - continue to authentication
                    if (registerStatusCode == 400 || registerStatusCode == 409) {
                        Log.d("ThingsRepo", "Device already exists on server, continuing to authentication")
                    } else {
                        // For other errors, fail
                        Log.e("ThingsRepo", "Device registration failed with status $registerStatusCode, cannot proceed")
                        return@withContext null
                    }
                }
                
                // STEP 2: Get token after device is registered (POST /v4/thingsapp/GetTokenForThingsApp)
                Log.d("ThingsRepo", "STEP 2: Authenticating to get token for deviceId: $deviceId")
                val breadcrumb = Breadcrumb("Starting authentication")
                breadcrumb.category = "auth"
                breadcrumb.level = io.sentry.SentryLevel.INFO
                breadcrumb.setData("device_id", deviceId)
                Sentry.addBreadcrumb(breadcrumb)
                
                val response = api.getToken(mapOf("DeviceId" to deviceId))
                if (response.isSuccessful) {
                    authToken = response.body()?.token
                    NetworkModule.setAuthToken(authToken)
                    
                    val successBreadcrumb = Breadcrumb("Authentication successful")
                    successBreadcrumb.category = "auth"
                    successBreadcrumb.level = io.sentry.SentryLevel.INFO
                    Sentry.addBreadcrumb(successBreadcrumb)
                    
                    Log.d("ThingsRepo", "✓ Authentication Successful (Status: ${response.code()}). Token: ${authToken?.take(10)}...")
                    authToken
                } else {
                    Log.e("ThingsRepo", "Authentication Failed. Status: ${response.code()}, Error: ${response.errorBody()?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("ThingsRepo", "CRITICAL: registerDeviceAndGetToken failed. Cause: ${e.javaClass.simpleName}, Msg: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "registerDeviceAndGetToken")
                    scope.setTag("device_id", deviceId)
                    scope.setContexts("auth", mapOf("device_id" to deviceId))
                    Sentry.captureException(e)
                }
                null
            }
        }
    }

    suspend fun authenticate(deviceId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val breadcrumb = Breadcrumb("Starting authentication")
                breadcrumb.category = "auth"
                breadcrumb.level = io.sentry.SentryLevel.INFO
                breadcrumb.setData("device_id", deviceId)
                Sentry.addBreadcrumb(breadcrumb)
                
                val response = api.getToken(mapOf("DeviceId" to deviceId))
                if (response.isSuccessful) {
                    authToken = response.body()?.token
                    NetworkModule.setAuthToken(authToken)
                    
                    val successBreadcrumb = Breadcrumb("Authentication successful")
                    successBreadcrumb.category = "auth"
                    successBreadcrumb.level = io.sentry.SentryLevel.INFO
                    Sentry.addBreadcrumb(successBreadcrumb)
                    
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
                Sentry.withScope { scope ->
                    scope.setTag("operation", "authenticate")
                    scope.setTag("device_id", deviceId)
                    scope.setContexts("auth", mapOf("device_id" to deviceId))
                    Sentry.captureException(e)
                }
                null
            }
        }
    }

    suspend fun syncDeviceInfo(context: Context, deviceId: String, stationCode: String?): DeviceInfoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val syncBreadcrumb = Breadcrumb("Syncing device info")
                syncBreadcrumb.category = "sync"
                syncBreadcrumb.level = io.sentry.SentryLevel.INFO
                syncBreadcrumb.setData("device_id", deviceId)
                syncBreadcrumb.setData("station_code", stationCode ?: "null")
                Sentry.addBreadcrumb(syncBreadcrumb)
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
                val wifiAddress = WifiUtils.getHashedWiFiBSSID(context)
                Log.d("ThingsRepo", "WiFi Address retrieved: $wifiAddress")
                
                val request = DeviceInfoRequest(
                    deviceId = deviceId,
                    stationCode = stationCode,
                    wifiAddress = wifiAddress
                )
                Log.d("ThingsRepo", "DeviceInfoRequest - DeviceId: ${request.deviceId}, WiFiAddress: ${request.wifiAddress}")
                
                val infoResponse = api.getDeviceInfo(request)

                if (infoResponse.isSuccessful) {
                    try {
                        // API returns nested structure: {"Data":{...},"StatusCode":200,"Message":"success"}
                        val apiResponse = infoResponse.body()
                        if (apiResponse != null && apiResponse.data != null) {
                            Log.d("ThingsRepo", "Device Info retrieved successfully: ${apiResponse.data.deviceId}")
                            apiResponse.data
                        } else {
                            Log.w("ThingsRepo", "Get Device Info returned null or empty data. Using default.")
                            createDefaultDeviceInfoResponse(deviceId)
                        }
                    } catch (e: com.google.gson.JsonSyntaxException) {
                        // API returned [] (empty array) instead of object
                        if (e.message?.contains("BEGIN_ARRAY") == true || 
                            e.message?.contains("Expected BEGIN_OBJECT") == true) {
                            Log.w("ThingsRepo", "API returned empty array for getDeviceInfo, using default device info")
                            createDefaultDeviceInfoResponse(deviceId)
                        } else {
                            Log.e("ThingsRepo", "JSON parsing error: ${e.message}", e)
                            Sentry.withScope { scope ->
                                scope.setTag("operation", "syncDeviceInfo")
                                scope.setTag("error_type", "json_parse")
                                scope.setContexts("sync", mapOf("device_id" to deviceId, "station_code" to (stationCode ?: "null")))
                                Sentry.captureException(e)
                            }
                            createDefaultDeviceInfoResponse(deviceId)
                        }
                    }
                } else {
                    Log.w("ThingsRepo", "Get Device Info failed: ${infoResponse.code()}. Using default.")
                    createDefaultDeviceInfoResponse(deviceId)
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                // Handle JSON parsing error - API returned [] instead of {}
                if (e.message?.contains("BEGIN_ARRAY") == true || 
                    e.message?.contains("Expected BEGIN_OBJECT") == true) {
                    Log.w("ThingsRepo", "API returned empty array for getDeviceInfo (caught in outer catch), using default")
                    createDefaultDeviceInfoResponse(deviceId)
                } else {
                    Log.e("ThingsRepo", "Sync Info JSON parsing failed: ${e.message}", e)
                    Sentry.withScope { scope ->
                        scope.setTag("operation", "syncDeviceInfo")
                        scope.setTag("error_type", "json_parse_outer")
                        scope.setContexts("sync", mapOf("device_id" to deviceId, "station_code" to (stationCode ?: "null")))
                        Sentry.captureException(e)
                    }
                    createDefaultDeviceInfoResponse(deviceId)
                }
            } catch (e: Exception) {
                Log.e("ThingsRepo", "Sync Info failed: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "syncDeviceInfo")
                    scope.setContexts("sync", mapOf("device_id" to deviceId, "station_code" to (stationCode ?: "null")))
                    Sentry.captureException(e)
                }
                createDefaultDeviceInfoResponse(deviceId) // Return default instead of null
            }
        }
    }

    suspend fun updateAlias(deviceId: String, newAlias: String): Boolean {
        return try {
            val aliasBreadcrumb = Breadcrumb("Updating device alias")
            aliasBreadcrumb.category = "user"
            aliasBreadcrumb.level = io.sentry.SentryLevel.INFO
            Sentry.addBreadcrumb(aliasBreadcrumb)
            val response = api.setDeviceAlias(SetDeviceAliasRequest(deviceId, newAlias))
            response.isSuccessful
        } catch (e: Exception) {
            Sentry.withScope { scope ->
                scope.setTag("operation", "updateAlias")
                scope.setContexts("alias", mapOf("device_id" to deviceId, "new_alias" to newAlias))
                Sentry.captureException(e)
            }
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
        context: Context,
        deviceId: String,
        watts: Double,
        kwh: Double,
        batteryLevel: Int,
        isCharging: Boolean,
        deviceInfo: DeviceInfoResponse? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        stationCode: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                val now = Instant.now()
                
                // Get WiFi address
                val wifiAddress = WifiUtils.getHashedWiFiBSSID(context)
                
                // Get emission factor from carbon intensity (convert gCO2/kWh to grams per kWh)
                // Carbon intensity is in gCO2/kWh, so we use it directly
                val emissionFactor = deviceInfo?.carbonInfo?.currentIntensity?.toInt() ?: 0
                
                // Calculate CO2 emissions: kWh * emissionFactor (gCO2/kWh) = grams CO2
                val totalGramsCO2 = kwh * emissionFactor
                
                // Get station info from deviceInfo
                val stationInfo = deviceInfo?.stationInfo
                
                val stationId = stationInfo?.stationId
                val isGreen = stationInfo?.isGreen ?: false
                val climateStatus = deviceInfo?.climateStatus
                
                // Get userId from deviceInfo
                val userId = deviceInfo?.userId
                
                val model = AndroidMeasurementModel(
                    deviceId = deviceId,
                    totalWatts = watts,
                    totalWattHours = kwh,
                    totalGramsCO2 = totalGramsCO2,
                    from = now.minusSeconds(60).toString(),
                    to = now.toString(),
                    batteryLevelFrom = batteryLevel,
                    batteryLevelTo = batteryLevel,
                    isCharging = isCharging,
                    sourceType = if (isCharging) "Charging" else "Battery",
                    batteryCapacity = BatteryUtil.getBatteryCapacity(context),
                    emissionFactor = emissionFactor,
                    cfeScore = if (isGreen) 1.0 else 0.0, // CFE score: 1.0 for green, 0.0 for not green
                    userId = userId,
                    appId = null, // App ID if available
                    wifiAddress = wifiAddress,
                    // stationCode is not sent in addConsumption request
                    stationId = stationId,
                    isGreen = isGreen,
                    climateStatus = climateStatus,
                    latitude = latitude,
                    longitude = longitude,
                    isVerifiedAsGreen = isGreen
                )

                Log.d("ThingsRepo", "Uploading consumption - kWh: $kwh, EmissionFactor: $emissionFactor, GramsCO2: $totalGramsCO2, Station: $stationCode, IsGreen: $isGreen")
                
                val consumptionBreadcrumb = Breadcrumb("Uploading consumption data")
                consumptionBreadcrumb.category = "api"
                consumptionBreadcrumb.level = io.sentry.SentryLevel.INFO
                consumptionBreadcrumb.setData("kwh", kwh.toString())
                consumptionBreadcrumb.setData("station_code", stationCode ?: "null")
                consumptionBreadcrumb.setData("is_green", isGreen.toString())
                Sentry.addBreadcrumb(consumptionBreadcrumb)
                val response = api.addDeviceConsumption(model)
                if (response.isSuccessful) {
                    Log.d("ThingsRepo", "Consumption upload successful (Status: ${response.code()})")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ThingsRepo", "Consumption upload failed (Status: ${response.code()}, Body: $errorBody)")
                    Sentry.withScope { scope ->
                        scope.setTag("operation", "uploadConsumption")
                        scope.setTag("http_status", response.code().toString())
                        scope.setContexts("consumption", mapOf(
                            "device_id" to deviceId,
                            "kwh" to kwh.toString(),
                            "station_code" to (stationCode ?: "null")
                        ))
                        scope.setExtra("error_body", errorBody ?: "")
                        Sentry.captureMessage("Consumption upload failed: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ThingsRepo", "Upload failed: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "uploadConsumption")
                    scope.setContexts("consumption", mapOf(
                        "device_id" to deviceId,
                        "kwh" to kwh.toString(),
                        "station_code" to (stationCode ?: "null")
                    ))
                    Sentry.captureException(e)
                }
            }
        }
    }

    suspend fun setStation(deviceId: String, stationCode: String): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val stationBreadcrumb = Breadcrumb("Setting station code")
                stationBreadcrumb.category = "user"
                stationBreadcrumb.level = io.sentry.SentryLevel.INFO
                stationBreadcrumb.setData("device_id", deviceId)
                stationBreadcrumb.setData("station_code", stationCode)
                Sentry.addBreadcrumb(stationBreadcrumb)
                
                val response = api.setStation(SetStationRequest(deviceId = deviceId, stationCode = stationCode))
                if (response.isSuccessful) {
                    val successBreadcrumb = Breadcrumb("Station code set successfully")
                    successBreadcrumb.category = "user"
                    successBreadcrumb.level = io.sentry.SentryLevel.INFO
                    Sentry.addBreadcrumb(successBreadcrumb)
                    Pair(true, null)
                } else {
                    // Try to extract a meaningful error message
                    val errorBody = response.errorBody()?.string()
                    Log.e("ThingsRepo", "SetStation failed: $errorBody")
                    
                    val errorMessage = try {
                        val gson = Gson()
                        val map = gson.fromJson(errorBody, Map::class.java)
                        map["Message"] as? String ?: (errorBody ?: "Validation failed")
                    } catch (e: Exception) {
                        errorBody ?: "Validation failed"
                    }
                    
                    Pair(false, errorMessage)
                }
            } catch (e: Exception) {
                Log.e("ThingsRepo", "SetStation exception: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "setStation")
                    scope.setContexts("station", mapOf("device_id" to deviceId, "station_code" to stationCode))
                    Sentry.captureException(e)
                }
                Pair(false, e.message ?: "Unknown error")
            }
        }
    }

    suspend fun authorizeGreenLogin(
        deviceId: String,
        sessionId: String,
        requestedBy: String,
        requestedUrl: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ThingsRepo", "=== Starting Green Login Auth ===")
                Log.d("ThingsRepo", "DeviceId: $deviceId")
                Log.d("ThingsRepo", "SessionId: $sessionId")
                Log.d("ThingsRepo", "RequestedBy: $requestedBy")
                Log.d("ThingsRepo", "RequestedUrl: $requestedUrl")
                
                val request = GreenLoginAuthRequest(
                    deviceId = deviceId,
                    sessionId = sessionId,
                    vendor = Build.MANUFACTURER,
                    name = Build.MODEL,
                    requestedBy = requestedBy,
                    requestedUrl = requestedUrl
                )
                
                Log.d("ThingsRepo", "Sending POST request to /v4/thingsapp/greenloginauth...")
                val response = api.greenLoginAuth(request)
                
                Log.d("ThingsRepo", "Response received - Status Code: ${response.code()}")
                
                // Check response code first - 200 means success regardless of body format
                // API returns [] (empty array) on success, so we just check status code
                if (response.code() == 200) {
                    Log.d("ThingsRepo", "✅ Green Login Auth successful (status 200)")
                    true
                } else {
                    Log.w("ThingsRepo", "❌ Green Login Auth failed with status: ${response.code()}")
                    false
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                // Handle JSON parsing error - API returned [] instead of {}
                // If we get here, the response was likely 200 but body parsing failed
                if (e.message?.contains("BEGIN_ARRAY") == true || 
                    e.message?.contains("Expected BEGIN_OBJECT") == true) {
                    Log.d("ThingsRepo", "API returned empty array for greenLoginAuth, treating as success")
                    true
                } else {
                    Log.w("ThingsRepo", "JSON parsing error: ${e.message}. Treating as success for 200 response.")
                    true // Still treat as success since API returned 200
                }
            } catch (e: Exception) {
                Log.e("ThingsRepo", "Green Login Auth failed: ${e.message}", e)
                Sentry.withScope { scope ->
                    scope.setTag("operation", "authorizeGreenLogin")
                    scope.setContexts("green_login", mapOf(
                        "device_id" to deviceId,
                        "session_id" to sessionId,
                        "requested_by" to requestedBy
                    ))
                    Sentry.captureException(e)
                }
                false
            }
        }
    }
}