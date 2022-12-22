package tedee.mobile.demo.bluetooth

import com.polidea.rxandroidble2.RxBleConnection
import io.reactivex.Single
import tedee.mobile.demo.BluetoothConstants
import tedee.mobile.demo.secure.SecureConnectionConstants
import tedee.mobile.demo.secure.SecureException
import tedee.mobile.demo.secure.SecureSession

class LockBTApi {

  var session: SecureSession? = null

  fun openLock(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendEncryptedNotificationCharacteristic(
      BluetoothConstants.OPEN_LOCK,
      byteArrayOf(0)
    )

  fun setNotificationsDisabled(rxBleConnection: RxBleConnection): Single<ByteArray> =
    rxBleConnection.sendEncryptedNotificationCharacteristic(
      BluetoothConstants.SET_NOTIFICATIONS_DISABLED
    )

  private fun RxBleConnection.sendEncryptedNotificationCharacteristic(
    message: Byte,
    payload: ByteArray? = null,
  ): Single<ByteArray> {
    return Single.create { emitter ->
      session?.let { secureSession ->
        buildEncryptedMessageToSend(secureSession, message, payload) { encryptedMessage ->
          updateCharacteristic(
            BluetoothConstants.LOCK_NOTIFICATION_CHARACTERISTIC,
            encryptedMessage
          ).subscribe({ emitter.onSuccess(it) }, { emitter.onError(it) })
        }
      } ?: emitter.onError(SecureException())
    }
  }

  // secure session / protocol
  fun sendHelloMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.MESSAGE_HELLO, message)
    )

  fun sendServerVerifyMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.SERVER_VERIFY, message)
    )

  fun sendClientVerifyMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.CLIENT_VERIFY, message)
    )

  fun sendClientVerifyEndMessage(
    rxBleConnection: RxBleConnection, message: ByteArray,
  ): Single<ByteArray> =
    rxBleConnection.sendSecureNotificationCharacteristic(
      buildSecureEstablishMessageToSend(SecureConnectionConstants.CLIENT_VERIFY_END, message)
    )

  private fun RxBleConnection.sendSecureNotificationCharacteristic(message: ByteArray) =
    updateCharacteristic(BluetoothConstants.SECURE_SESSION_LOCK_SEND_CHARACTERISTIC, message)
}
