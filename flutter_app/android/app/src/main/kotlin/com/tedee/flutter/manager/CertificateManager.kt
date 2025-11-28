package com.tedee.flutter.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tedee.flutter.api.data.model.MobileRegistrationBody
import com.tedee.flutter.api.service.MobileService
import com.tedee.flutter.datastore.DataStoreManager
import tedee.mobile.sdk.ble.keystore.getMobilePublicKey
import tedee.mobile.sdk.ble.model.DeviceCertificate
import timber.log.Timber

/**
 * Certificate Manager for Flutter
 * Simplified version that returns DeviceCertificate directly
 */
class CertificateManager(
  private val context: Context
) {
  private val mobileService by lazy { MobileService() }

  /**
   * Get or generate certificate for lock
   * Returns DeviceCertificate ready for LockConnectionManager.connect()
   */
  suspend fun registerAndGenerateCertificate(
    serialNumber: String,
    deviceId: String,
    name: String
  ): DeviceCertificate {
    return withContext(Dispatchers.IO) {
      try {
        // Check if we have a valid cached certificate
        val cachedCert = DataStoreManager.getCertificate(context)
        val cachedDevicePublicKey = DataStoreManager.getDevicePublicKey(context)

        if (cachedCert.isNotEmpty() && cachedDevicePublicKey.isNotEmpty()) {
          Timber.d("Using cached certificate")
          return@withContext DeviceCertificate(
            certificate = cachedCert,
            devicePublicKey = cachedDevicePublicKey
          )
        }

        // Generate new certificate
        Timber.d("Generating new certificate for $name (S/N: $serialNumber, Device ID: $deviceId)")

        // Get mobile public key
        val mobilePublicKey = DataStoreManager.getMobilePublicKey(context)
          ?: getMobilePublicKey()
          ?: throw Exception("Failed to get mobile public key")

        // Register mobile device
        val registrationResult = mobileService.registerMobile(
          MobileRegistrationBody(name, publicKey = mobilePublicKey)
        )
        Timber.d("Mobile registered with ID: ${registrationResult.id}")

        // Get certificate from Tedee API
        val certificateResult = mobileService.getCertificate(
          registrationResult.id,
          deviceId.toInt()
        )
        Timber.d("Certificate received, expires: ${certificateResult.expirationDate}")

        // Save certificate data
        DataStoreManager.saveCertificateData(
          context,
          certificateResult.certificate,
          certificateResult.devicePublicKey,
          certificateResult.mobilePublicKey
        )

        // Return DeviceCertificate
        DeviceCertificate(
          certificate = certificateResult.certificate,
          devicePublicKey = certificateResult.devicePublicKey
        )
      } catch (e: Exception) {
        Timber.e(e, "Failed to generate certificate")
        throw e
      }
    }
  }
}
