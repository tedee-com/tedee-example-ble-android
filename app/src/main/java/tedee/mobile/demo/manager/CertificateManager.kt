package tedee.mobile.demo.manager

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedee.mobile.demo.certificate.data.model.MobileCertificateResponse
import tedee.mobile.demo.certificate.data.model.MobileRegistrationBody
import tedee.mobile.demo.certificate.data.model.RegisterMobileResponse
import tedee.mobile.demo.datastore.DataStoreManager.getMobilePublicKey
import tedee.mobile.demo.certificate.service.MobileService
import tedee.mobile.demo.datastore.DataStoreManager
import tedee.mobile.sdk.ble.keystore.getMobilePublicKey

class CertificateManager(
  private val context: Context,
  private val lifecycleScope: LifecycleCoroutineScope,
) {
  var certificate: String = ""
  var devicePublicKey: String = ""
  private var mobilePublicKey: String = ""
  private val mobileService by lazy { MobileService() }

  fun setupMobilePublicKey() {
    lifecycleScope.launch {
      mobilePublicKey =
        getMobilePublicKey(context) ?: getMobilePublicKey().orEmpty()
    }
  }

  fun setupCertificateData() {
    lifecycleScope.launch {
      certificate = DataStoreManager.getCertificate(context)
      devicePublicKey = DataStoreManager.getDevicePublicKey(context)
    }
  }

  fun registerAndGenerateCertificate(
    deviceName: String, deviceId: Int,
    onResetCertificateStatus: () -> Unit,
    onSuccess: (certificateResponse: MobileCertificateResponse, registerResponse: RegisterMobileResponse) -> Unit,
    onFailure: (error: Exception) -> Unit,
  ) {
    onResetCertificateStatus()
    lifecycleScope.launch {
      try {
        val registrationResult = mobileService.registerMobile(
          MobileRegistrationBody(deviceName, publicKey = mobilePublicKey)
        )
        val certificateResult = mobileService.getCertificate(registrationResult.id, deviceId)
        withContext(Dispatchers.IO) {
          DataStoreManager.saveCertificateData(
            context,
            certificateResult.certificate,
            certificateResult.devicePublicKey,
            certificateResult.mobilePublicKey
          )
        }
        certificate = certificateResult.certificate
        devicePublicKey = certificateResult.devicePublicKey
        onSuccess(certificateResult, registrationResult)
      } catch (error: Exception) {
        onFailure(error)
      }
    }
  }
}
