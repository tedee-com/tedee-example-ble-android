package tedee.mobile.demo.manager

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import tedee.mobile.demo.certificate.service.MobileService
import tedee.mobile.sdk.ble.model.SignedTime

class SignedTimeManager(private val lifecycleScope: LifecycleCoroutineScope) {
  private val mobileService by lazy { MobileService() }

  fun getSignedTime(onSuccess: (SignedTime) -> Unit, onFailure: (error: Exception) -> Unit) {
    lifecycleScope.launch {
      try {
        val signedTime = mobileService.getSignedTime()
        onSuccess(signedTime)
      } catch (error: Exception) {
        onFailure(error)
      }
    }
  }

  suspend fun getSignedTime(): SignedTime {
    return try {
      mobileService.getSignedTime()
    } catch (error: Exception) {
      throw error
    }
  }
}
