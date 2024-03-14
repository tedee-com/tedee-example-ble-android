package tedee.mobile.demo.certificate.service

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import tedee.mobile.demo.certificate.data.model.MobileRegistrationBody

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
}