package tedee.mobile.demo.bluetooth

import com.polidea.rxandroidble2.RxBleConnection
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import tedee.mobile.demo.secure.SecureInteractorInterface
import tedee.mobile.demo.secure.SecureSession
import timber.log.Timber

class LockBluetoothApiInteractor(
  private val rxBleConnection: RxBleConnection,
) : SecureInteractorInterface {

  private val doorLockBTApi = LockBTApi()
  private val disposables: CompositeDisposable = CompositeDisposable()

  fun openLock() =
    doorLockBTApi
      .openLock(rxBleConnection)
      .setupSubscriber("openLock")

  fun disableNotifications() =
    doorLockBTApi
      .setNotificationsDisabled(rxBleConnection)
      .setupSubscriber("setNotificationsDisabled")

  override fun sendHello(message: ByteArray) =
    doorLockBTApi
      .sendHelloMessage(rxBleConnection, message)
      .setupSubscriber("sendHello")

  override fun sendServerVerify(message: ByteArray) =
    doorLockBTApi
      .sendServerVerifyMessage(rxBleConnection, message)
      .setupSubscriber("sendServerVerify")

  override fun sendClientVerify(message: ByteArray) =
    doorLockBTApi
      .sendClientVerifyMessage(rxBleConnection, message)
      .setupSubscriber("sendClientVerify")

  override fun sendClientVerifyEnd(message: ByteArray) =
    doorLockBTApi
      .sendClientVerifyEndMessage(rxBleConnection, message)
      .setupSubscriber("sendClientVerifyEnd")

  fun setSecureSession(session: SecureSession) {
    doorLockBTApi.session = session
  }

  fun close() = disposables.clear()

  private fun Single<ByteArray>.setupSubscriber(functionName: String) {
    subscribe(
      { Timber.d("$functionName()::onSuccess()") },
      { Timber.e(it, "$functionName()::onError()") }
    ).addTo(disposables)
  }
}
