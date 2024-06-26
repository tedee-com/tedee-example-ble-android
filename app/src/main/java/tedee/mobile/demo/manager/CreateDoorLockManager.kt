package tedee.mobile.demo.manager

import tedee.mobile.demo.api.data.model.NewDoorLockResponse
import tedee.mobile.demo.api.service.MobileService
import tedee.mobile.sdk.ble.model.CreateDoorLockData

class CreateDoorLockManager {
  private val mobileService by lazy { MobileService() }

  suspend fun createNewDoorLock(createDoorLockResponse: CreateDoorLockData): NewDoorLockResponse {
    return try {
      mobileService.createNewDoorLock(createDoorLockResponse)
    } catch (error: Exception) {
      throw error
    }
  }
}