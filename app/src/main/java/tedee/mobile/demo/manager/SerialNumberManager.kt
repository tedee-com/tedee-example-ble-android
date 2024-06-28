package tedee.mobile.demo.manager

import tedee.mobile.demo.api.service.MobileService

class SerialNumberManager {
  private val mobileService by lazy { MobileService() }

  suspend fun getSerialNumber(activationCode: String): String {
    return try {
      mobileService.getSerialNumber(activationCode)
    } catch (error: Exception) {
      throw error
    }
  }
}