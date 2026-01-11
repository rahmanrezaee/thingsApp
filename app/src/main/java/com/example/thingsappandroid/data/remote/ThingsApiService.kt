package com.example.thingsappandroid.data.remote

import com.example.thingsappandroid.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ThingsApiService {

    @POST("/v4/thingsapp/GetTokenForThingsApp")
    suspend fun getToken(@Body request: Map<String, String>): Response<TokenResponse>

    @POST("/v4/thingsapp/registerdevice")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<Unit>

    @POST("/v4/thingsapp/getdeviceinfo")
    suspend fun getDeviceInfo(@Body request: DeviceInfoRequest): Response<DeviceInfoResponse>

    @POST("/v4/androidapp/adddeviceconsumption")
    suspend fun addDeviceConsumption(@Body request: AndroidMeasurementRequest): Response<MeasurementResponse>
}