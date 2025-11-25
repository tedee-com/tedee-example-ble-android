package com.tedee.flutter.api.service

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.tedee.flutter.api.data.model.MobileRegistrationBody
import tedee.mobile.sdk.ble.model.CreateDoorLockData

interface MobileApi {
  @POST("api/v1.32/my/mobile")
  suspend fun registerMobile(@Body body: MobileRegistrationBody): Response<JsonElement>

  @GET("api/v1.32/my/devicecertificate/getformobile")
  suspend fun getCertificate(
    @Query("MobileId") mobileId: String,
    @Query("DeviceId") deviceId: Int,
  ): Response<JsonElement>

  @GET("api/v1.32/datetime/getsignedtime")
  suspend fun getSignedTime(): Response<JsonElement>

  @GET("api/v1.32/my/device/getserialnumber")
  suspend fun getSerialNumber(@Query("ActivationCode") activationCode: String): Response<JsonElement>

  @POST("/api/v1.32/my/Lock")
  suspend fun createNewDoorLock(@Body newDoorLock: CreateDoorLockData): Response<JsonElement>
}
