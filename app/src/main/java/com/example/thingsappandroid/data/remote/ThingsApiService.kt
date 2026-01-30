package com.example.thingsappandroid.data.remote

import com.example.thingsappandroid.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap

interface ThingsApiService {

    // --- Authentication & Device ---

    @POST("/v4/thingsapp/GetTokenForThingsApp")
    suspend fun getToken(@Body request: Map<String, String>): Response<TokenResponse>

    @POST("/v4/thingsapp/registerdevice")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<ResponseBody> // API returns [] on success

    @POST("/v4/thingsapp/getdeviceinfo")
    suspend fun getDeviceInfo(@Body request: DeviceInfoRequest): Response<DeviceInfoApiResponse>

    @POST("/v4/thingsapp/setdevicealias")
    suspend fun setDeviceAlias(@Body request: SetDeviceAliasRequest): Response<BasicResponse>

    @POST("/v4/thingsapp/setstation")
    suspend fun setStation(@Body request: SetStationRequest): Response<BasicResponse>

    @POST("/v4/thingsapp/getstationinfo")
    suspend fun getStationInfo(@Body request: GetStationInfoRequest): Response<StationInfo>

    @POST("/v4/thingsapp/setorganization")
    suspend fun setOrganization(@Body request: SetOrganizationRequest): Response<BasicResponse>

    @POST("/v4/thingsapp/getorganizationinfo")
    suspend fun getOrganizationInfo(@Body request: GetOrganizationInfoRequest): Response<OrganizationInfo>

    @POST("/v4/thingsapp/getclimatestatus")
    suspend fun getClimateStatus(@Body request: GetClimateStatusRequest): Response<ClimateStatusResponse>

    @POST("/v4/thingsapp/setclimatestatus")
    suspend fun setClimateStatus(@Body request: SetClimateStatusRequest): Response<SetClimateStatusResponse>

    // --- Green Info & Intensity ---

    @POST("/v4/thingsapp/getgreenfiinfo")
    suspend fun getGreenFiInfo(@Body request: GetGreenFiInfoRequest): Response<GreenFiInfoResponse>

    @POST("/v4/thingsapp/getlatestintensity")
    suspend fun getLatestIntensity(@Body request: GetLatestIntensityRequest): Response<LatestIntensityResponse>

    @POST("/v4/thingsapp/greenloginauth")
    suspend fun greenLoginAuth(@Body request: GreenLoginAuthRequest): Response<BasicResponse>

    // --- Apps & Consumption ---

    @POST("/v4/thingsapp/addappconsumption")
    suspend fun addAppConsumption(@Body request: AppConsumptionRequest): Response<MeasurementResponse>

    @POST("/v4/thingsapp/addappconsumptionrange")
    suspend fun addAppConsumptionRange(@Body request: List<AppConsumptionRequest>): Response<BasicResponse>

    @GET("/v4/thingsapp/getappsformeasurement")
    suspend fun getAppsForMeasurement(@QueryMap options: Map<String, Int>): Response<List<AppItemResponse>>

    // --- Android Specific ---

    @POST("/v4/androidapp/getlatestversion")
    suspend fun getLatestVersion(@Body request: GetLatestVersionRequest): Response<VersionInfo>

    @POST("/v4/androidapp/getlatestmessages")
    suspend fun getLatestMessages(): Response<LatestMessagesResponse>

    @POST("/v4/androidapp/adddeviceconsumption")
    suspend fun addDeviceConsumption(@Body request: AndroidMeasurementModel): Response<MeasurementResponse>

    @POST("/v4/androidapp/adddeviceconsumptionrange")
    suspend fun addDeviceConsumptionRange(@Body request: List<AndroidMeasurementModel>): Response<BasicResponse>
}