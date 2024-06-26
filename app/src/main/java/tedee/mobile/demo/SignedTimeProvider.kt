package tedee.mobile.demo

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import tedee.mobile.demo.helper.UiHelper
import tedee.mobile.sdk.ble.bluetooth.ISignedTimeProvider
import tedee.mobile.sdk.ble.model.SignedTime

class SignedTimeProvider(
  private val lifecycleScope: LifecycleCoroutineScope,
  private val uiSetupHelper: UiHelper,
) : ISignedTimeProvider {
  override fun getSignedTime(callback: (SignedTime) -> Unit) {
    lifecycleScope.launch {
      try {
        val result = uiSetupHelper.getSignedTime()
        callback(result)
      } catch (error: Exception) {
        uiSetupHelper.onFailureRequest(error)
      }
    }
  }
}