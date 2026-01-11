package com.example.thingsappandroid.data.model

import com.google.gson.annotations.SerializedName

// --- Requests ---

data class RegisterDeviceRequest(
    @SerializedName("DeviceId") val deviceId: String,
    @SerializedName("Name") val name: String,
    @SerializedName("Make") val make: String,
    @SerializedName("OS") val os: String,
    @SerializedName("Category") val category: String,
    @SerializedName("SerialNumber") val serialNumber: String
)

data class DeviceInfoRequest(
    @SerializedName("DeviceId") val deviceId: String,
    @SerializedName("StationCode") val stationCode: String? = null,
    @SerializedName("WiFiAddress") val wifiAddress: String? = null,
    @SerializedName("Latitude") val latitude: Double = 0.0,
    @SerializedName("Longitude") val longitude: Double = 0.0
)

data class AndroidMeasurementRequest(
    @SerializedName("AndroidMeasurementModel") val model: AndroidMeasurementModel
)

data class AndroidMeasurementModel(
    val userId: String? = null,
    val deviceId: String,
    val totalWatts: Double,
    val totalWattHours: Double,
    val totalGramsCO2: Double,
    val from: String, // ISO8601
    val to: String,   // ISO8601
    val batteryLevelFrom: Int,
    val batteryLevelTo: Int,
    val isCharging: Boolean,
    val wifiAddress: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

// --- Responses ---

data class TokenResponse(
    @SerializedName("Data") val token: String
)

data class DeviceInfoResponse(
    @SerializedName("DeviceId") val deviceId: String,
    @SerializedName("ClimateStatus") val climateStatus: Int?, // Made nullable to prevent mapping crash
    @SerializedName("TotalAvoidedKgCO2e") val totalAvoided: Double?, // Made nullable
    @SerializedName("TotalElectricityConsumedKWh") val totalConsumed: Double?, // Made nullable
    @SerializedName("StationInfo") val stationInfo: StationInfo?,
    @SerializedName("CarbonIntensityInfo") val carbonInfo: CarbonIntensityInfo?
)

data class StationInfo(
    @SerializedName("StationName") val stationName: String,
    @SerializedName("IsGreen") val isGreen: Boolean
)

data class CarbonIntensityInfo(
    @SerializedName("CurrentIntensity") val currentIntensity: Double
)

data class MeasurementResponse(
    val measurementId: String
)

data class BasicResponse(
    val success: Boolean
)