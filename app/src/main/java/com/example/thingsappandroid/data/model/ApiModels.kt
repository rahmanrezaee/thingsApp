package com.example.thingsappandroid.data.model

import com.google.gson.annotations.SerializedName

// --- Enums ---

enum class ClimateStatus(val value: Int) {
    NotSet(0),
    NotGreenOnContract(1),
    NotGreenOnCarbonBudget(2),
    NotGreenOnBoth(3),
    NotGreenOnDeviceCarbonBudget(4),
    GreenOnContract(5),
    GreenOnCarbonBudget(6),
    GreenOnBoth(7),
    GreenOnDeviceCarbonBudget(8),
    GreenOnEACs(9);

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: NotSet
    }
}

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
    @SerializedName("Longitude") val longitude: Double = 0.0,
    @SerializedName("CurrentVersion") val currentVersion: String? = null
)

data class AndroidMeasurementRequest(
    @SerializedName("AndroidMeasurementModel") val model: AndroidMeasurementModel
)

data class AndroidMeasurementRangeRequest(
    @SerializedName("AndroidMeasurementModel") val models: List<AndroidMeasurementModel>
)

data class AndroidMeasurementModel(
    val deviceId: String,
    val totalWatts: Double,
    val totalWattHours: Double,
    val totalGramsCO2: Double,
    val from: String, // ISO8601
    val to: String,   // ISO8601
    val batteryLevelFrom: Int,
    val batteryLevelTo: Int,
    
    // Existing code usage compatibility
    val isCharging: Boolean? = null,

    // New Spec Fields
    val averageAmpere: Double? = null,
    val averageVoltage: Double? = null,
    val interval: Int? = null,
    val totalSamples: Int? = null,
    val sourceType: String? = null,
    val batteryCapacity: Double? = null,
    val emissionFactor: Double? = null,
    val cfeScore: Double? = null,
    val userId: String? = null,
    val appId: String? = null,
    @SerializedName("wiFiAddress") val wifiAddress: String? = null,
    val stationCode: String? = null,
    val stationId: String? = null,
    val isGreen: Boolean? = null,
    val climateStatus: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val city: String? = null,
    val country: String? = null,
    val isVerifiedAsGreen: Boolean? = null,
    val statusMessage: String? = null
)

data class AppConsumptionRequest(
    @SerializedName("AppConsumptionModel") val model: AppConsumptionModel
)

data class AppConsumptionModel(
    val dateTime: String,
    val totalHours: Double,
    val wattHours: Double,
    val gramsCO2: Double,
    val deviceId: String,
    val cpuUsage: Double,
    val ramUsageMB: Double,
    val gpuUsage: Double,
    val otherUsage: Double? = null,
    val climateStatus: String, // Enum name as string in spec example
    val clientFileName: String,
    val clientFileDescription: String,
    val stationId: String?,
    val appId: String
)

data class VerifyDeviceRequestWrapper(
    @SerializedName("VerifyDeviceRequestModel") val model: VerifyDeviceRequestModel
)

data class VerifyDeviceRequestModel(
    val name: String,
    val make: String,
    val category: String,
    val os: String,
    val deviceId: String,
    val appId: String,
    val wiFiAddress: String,
    val latitude: Double,
    val longitude: Double,
    val stationCode: String,
    val serialNumber: String
)

data class GetGreenFiInfoRequest(@SerializedName("WiFiAddress") val wifiAddress: String)
data class GetLatestIntensityRequest(@SerializedName("Latitude") val lat: Double, @SerializedName("Longitude") val lon: Double)
data class SetDeviceAliasRequest(@SerializedName("DeviceId") val deviceId: String, @SerializedName("Alias") val alias: String)
data class SetStationRequest(@SerializedName("DeviceId") val deviceId: String, @SerializedName("StationCode") val stationCode: String)
data class GetStationInfoRequest(@SerializedName("DeviceId") val deviceId: String)
data class SetOrganizationRequest(@SerializedName("DeviceId") val deviceId: String, @SerializedName("OrganizationCode") val orgCode: String)
data class GetOrganizationInfoRequest(@SerializedName("DeviceId") val deviceId: String)
data class GetClimateStatusRequest(@SerializedName("DeviceId") val deviceId: String)
data class GreenLoginAuthRequest(
    @SerializedName("DeviceId") val deviceId: String,
    @SerializedName("SessionId") val sessionId: String,
    @SerializedName("Vendor") val vendor: String,
    @SerializedName("Name") val name: String,
    @SerializedName("RequestedBy") val requestedBy: String,
    @SerializedName("RequestedUrl") val requestedUrl: String
)
data class GetAppsForMeasurementRequest(@SerializedName("osType") val osType: Int)
data class GetLatestVersionRequest(@SerializedName("currentVersion") val currentVersion: String)

// --- Responses ---

data class TokenResponse(
    @SerializedName("Data") val token: String
)

data class DeviceInfoResponse(
    @SerializedName("DeviceId") val deviceId: String,
    @SerializedName("Alias") val alias: String?,
    @SerializedName("CommencementDate") val commencementDate: String?,
    @SerializedName("ThingId") val thingId: String?,
    @SerializedName("ClimateStatus") val climateStatus: String?, // Enum as String or Int? Spec says enum. Usually int or string.
    @SerializedName("TotalAvoidedKgCO2e") val totalAvoided: Double?,
    @SerializedName("TotalEmissionsKgCO2e") val totalEmissions: Double?,
    @SerializedName("TotalElectricityConsumedKWh") val totalConsumed: Double?,
    @SerializedName("TotalEmissionsBudgetKgCO2e") val totalBudget: Double?,
    @SerializedName("RemainingEmissionsBudgetKgCO2e") val remainingBudget: Double?,
    @SerializedName("UserId") val userId: String?,
    @SerializedName("StationInfo") val stationInfo: StationInfo?,
    @SerializedName("OrganizationInfo") val organizationInfo: OrganizationInfo?,
    @SerializedName("CarbonIntensityInfo") val carbonInfo: CarbonIntensityInfo?,
    @SerializedName("VersionInfo") val versionInfo: VersionInfo?
)

data class StationInfo(
    @SerializedName("StationName") val stationName: String?,
    @SerializedName("StationId") val stationId: String?,
    @SerializedName("IsGreen") val isGreen: Boolean?,
    @SerializedName("ClimateStatus") val climateStatus: String?, // Enum
    @SerializedName("Country") val country: String?,
    @SerializedName("UtilityName") val utilityName: String?,
    @SerializedName("WiFiAddress") val wifiAddress: String?
)

data class OrganizationInfo(
    @SerializedName("Id") val id: String,
    @SerializedName("Code") val code: String,
    @SerializedName("Name") val name: String
)

data class CarbonIntensityInfo(
    @SerializedName("CurrentIntensity") val currentIntensity: Double,
    @SerializedName("Source") val source: String?,
    @SerializedName("RetrievedAt") val retrievedAt: String?
)

data class VersionInfo(
    @SerializedName("CurrentVersion") val currentVersion: String?,
    @SerializedName("LatestVersion") val latestVersion: String?,
    @SerializedName("DownloadUrl") val downloadUrl: String?
)

data class LatestIntensityResponse(
    @SerializedName("CurrentIntensity") val currentIntensity: Double,
    @SerializedName("Source") val source: String?,
    @SerializedName("RetrievedAt") val retrievedAt: String?,
    @SerializedName("Zone") val zone: String?
)

data class MeasurementResponse(val measurementId: String)
data class BasicResponse(val success: Boolean)
data class GreenFiInfoResponse(@SerializedName("isGreen") val isGreen: Boolean)

data class ClimateStatusResponse(@SerializedName("ClimateStatus") val climateStatus: String)

data class SetClimateStatusResponse(
    @SerializedName("Data") val data: SetClimateStatusData
)

data class SetClimateStatusData(
    val isAssociated: Boolean,
    val stationCode: String?,
    val stationId: String?,
    val organizationId: String?,
    val userId: String?,
    val isGreen: Boolean,
    val climateStatus: String,
    val message: String?
)

data class AppItemResponse(
    @SerializedName("Id") val id: String,
    @SerializedName("FileName") val fileName: String,
    @SerializedName("AppName") val appName: String
)

data class LatestMessagesResponse(@SerializedName("status") val status: String)