package tedee.mobile.demo.helper

import tedee.mobile.sdk.ble.model.SignedTime

interface UiHelper {
  suspend fun getSignedTime(): SignedTime
  fun onFailureRequest(error: Throwable)
}