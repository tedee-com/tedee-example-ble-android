package tedee.mobile.demo.certificate.service

import com.google.gson.JsonElement
import retrofit2.Response
import tedee.mobile.demo.certificate.data.model.MobileCertificateResponse
import tedee.mobile.demo.certificate.data.model.MobileRegistrationBody
import tedee.mobile.demo.certificate.data.model.RegisterMobileResponse

class MobileService {

  suspend fun registerMobile(body: MobileRegistrationBody): RegisterMobileResponse {
    val response = ApiProvider.provideApi().registerMobile(body)
    return processResponse(response, RegisterMobileResponse::class.java)
  }

  suspend fun getCertificate(mobileId: String, deviceId: Int): MobileCertificateResponse {
    val response = ApiProvider.provideApi().getCertificate(mobileId, deviceId)
    return processResponse(response, MobileCertificateResponse::class.java)
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

  private fun parseError(response: Response<JsonElement>): String {
    val errorWrapper =
      response.errorBody()?.let { ApiProvider.provideGson().fromJson(it.charStream(), ErrorWrapper::class.java) }
    val responseError = errorWrapper?.errorMessages?.joinToString(separator = "|")
    return "${errorWrapper?.statusCode}: ${responseError ?: "Unknown error"}"
  }

  class ErrorWrapper(
    var errorMessages: List<String>?,
    var statusCode: Int,
  )
}