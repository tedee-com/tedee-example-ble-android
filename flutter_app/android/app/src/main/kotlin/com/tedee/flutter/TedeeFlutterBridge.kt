package com.tedee.flutter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.model.DeviceCertificate
import timber.log.Timber
import com.tedee.flutter.manager.CertificateManager

/**
 * Bridge between Flutter and Tedee Android SDK
 *
 * This class encapsulates all the logic for interacting with Tedee locks
 * and can be reused across different Android native implementations.
 */
class TedeeFlutterBridge(
    private val context: Context,
    private val lockConnectionManager: LockConnectionManager
) {
    private val certificateManager = CertificateManager(context)

    /**
     * Connect to a Tedee lock
     *
     * @param serialNumber Lock serial number (e.g., "10530206-030484")
     * @param deviceId Lock device ID (e.g., "273450")
     * @param name Lock name (e.g., "Lock-40C5")
     * @param keepConnection Whether to keep the connection alive
     * @param listener Callback listener for lock events
     *
     * @return DeviceCertificate if successful
     */
    suspend fun connect(
        serialNumber: String,
        deviceId: String,
        name: String,
        keepConnection: Boolean,
        listener: ILockConnectionListener
    ): DeviceCertificate {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("TedeeFlutterBridge: Connecting to lock $name (S/N: $serialNumber)")

                // Step 1: Get or generate certificate
                val certificate = certificateManager.registerAndGenerateCertificate(
                    serialNumber = serialNumber,
                    deviceId = deviceId,
                    name = name
                )

                // Step 2: Connect to lock with certificate
                lockConnectionManager.connect(
                    serialNumber = serialNumber,
                    deviceCertificate = certificate,
                    keepConnection = keepConnection,
                    secureConnectionListener = listener
                )

                Timber.d("TedeeFlutterBridge: Connection initiated successfully")
                certificate
            } catch (e: Exception) {
                Timber.e(e, "TedeeFlutterBridge: Failed to connect to lock")
                throw e
            }
        }
    }

    /**
     * Disconnect from lock
     */
    fun disconnect() {
        Timber.d("TedeeFlutterBridge: Disconnecting from lock")
        lockConnectionManager.disconnect()
    }

    /**
     * Clear lock connection manager resources
     */
    fun clear() {
        Timber.d("TedeeFlutterBridge: Clearing lock connection manager")
        lockConnectionManager.clear()
    }
}
