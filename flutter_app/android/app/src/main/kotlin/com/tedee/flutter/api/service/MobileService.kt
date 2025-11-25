package com.tedee.flutter.api.service

import com.google.gson.JsonElement
import retrofit2.Response
import com.tedee.flutter.api.data.model.MobileCertificateResponse
import com.tedee.flutter.api.data.model.MobileRegistrationBody
import com.tedee.flutter.api.data.model.NewDoorLockResponse
import com.tedee.flutter.api.data.model.RegisterMobileResponse
import tedee.mobile.sdk.ble.model.CreateDoorLockData
import tedee.mobile.sdk.ble.model.SignedTime

class MobileService {

  suspend fun registerMobile(body: MobileRegistrationBody): RegisterMobileResponse {
    val response = ApiProvider.provideApi().registerMobile(body)
    return processResponse(response, RegisterMobileResponse::class.java)
  }

  suspend fun getCertificate(mobileId: String, deviceId: Int): MobileCertificateResponse {
    val response = ApiProvider.provideApi().getCertificate(mobileId, deviceId)
    return processResponse(response, MobileCertificateResponse::class.java)
  }

  suspend fun getSignedTime(): SignedTime {
    val response = ApiProvider.provideApi().getSignedTime()
    return processResponse(response, SignedTime::class.java)
  }

  suspend fun getSerialNumber(activationCode: String): String {
    val response = ApiProvider.provideApi().getSerialNumber(activationCode)
    return processPrimitiveResponse(response, "serialNumber")
  }

  suspend fun createNewDoorLock(data: CreateDoorLockData): NewDoorLockResponse {
    val response = ApiProvider.provideApi().createNewDoorLock(data)
    return processResponse(response, NewDoorLockResponse::class.java)
  }

  private fun <T> processResponse(response: Response<JsonElement>, clazz: Class<T>): T {
    if (response.isSuccessful) {
      val result = response.body()?.asJsonObject?.get("result")
      if (result?.isJsonObject == true) {
        return ApiProvider.provideGson().fromJson(result, clazz)
      } else {
        throw Exception("Result is not a JsonObject")
      }
    } else {
      throw Exception(parseError(response))
    }
  }

  private fun processPrimitiveResponse(
    response: Response<JsonElement>,
    memberName: String,
  ): String {
    if (response.isSuccessful) {
      val result = response.body()?.asJsonObject?.get("result")
      if (result?.isJsonObject == true) {
        return result.asJsonObject.get(memberName).asString
      } else {
        throw Exception("Result is not a JsonObject")
      }
    } else {
      throw Exception(parseError(response))
    }
  }

  private fun parseError(response: Response<JsonElement>): String {
    val errorWrapper =
      response.errorBody()
        ?.let { ApiProvider.provideGson().fromJson(it.charStream(), ErrorWrapper::class.java) }
    val responseError = errorWrapper?.errorMessages?.joinToString(separator = "|")
    return "${errorWrapper?.statusCode}: ${responseError ?: "Unknown error"}"
  }

  class ErrorWrapper(
    var errorMessages: List<String>?,
    var statusCode: Int,
  )
}
