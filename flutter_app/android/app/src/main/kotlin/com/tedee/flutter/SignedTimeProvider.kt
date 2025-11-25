package com.tedee.flutter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tedee.mobile.sdk.ble.bluetooth.ISignedTimeProvider
import tedee.mobile.sdk.ble.model.SignedTime
import timber.log.Timber
import com.tedee.flutter.api.service.MobileService

class SignedTimeProvider(
    private val scope: CoroutineScope
) : ISignedTimeProvider {
    private val mobileService by lazy { MobileService() }

    override fun getSignedTime(callback: (SignedTime) -> Unit) {
        scope.launch {
            try {
                val signedTime = mobileService.getSignedTime()
                Timber.d("SignedTimeProvider: Got signed time successfully")
                callback(signedTime)
            } catch (error: Exception) {
                Timber.e(error, "SignedTimeProvider: Failed to get signed time")
                // SDK requires the callback to be called, so we throw the error
                // This will be handled by the SDK's error handler
                throw error
            }
        }
    }
}
