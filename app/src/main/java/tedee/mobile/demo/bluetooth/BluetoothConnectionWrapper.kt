package tedee.mobile.demo.bluetooth

import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import tedee.mobile.demo.BluetoothConstants
import tedee.mobile.demo.extentions.print
import tedee.mobile.demo.model.DeviceCertificate
import tedee.mobile.demo.secure.SecureConnectionConstants
import tedee.mobile.demo.secure.SecureConnectionHelper
import tedee.mobile.demo.secure.SecureSession
import tedee.mobile.demo.secure.SecurityData
import timber.log.Timber
import java.util.*
import kotlin.experimental.and

class BluetoothConnectionWrapper(
  private val serialNumber: String,
  private val bleDevice: RxBleDevice,
  private val accessCertificate: DeviceCertificate,
  private var wrapperListener: BluetoothWrapperListener,
) {
  private var lockInteractor: LockBluetoothApiInteractor? = null
  private var rxBleConnection: RxBleConnection? = null
  private var secureConnectionHelper: SecureConnectionHelper? = null
  private var session: SecureSession? = null
  private var compositeDisposable: CompositeDisposable = CompositeDisposable()
  private lateinit var secureEstablishNotifications: Observable<ByteArray>

  fun connect() {
    bleDevice.establishConnection(false)
      .doOnSubscribe { Timber.d("Connect lock: ${serialNumber}: establish bluetooth connection from wrapper") }
      .flatMap(
        {
          it.setupNotification(UUID.fromString(BluetoothConstants.SECURE_SESSION_LOCK_READ_NOTIFICATION_CHARACTERISTIC))
        },
        { bleConnection, secureNotifications ->
          ConnectionData(bleConnection, secureNotifications.map { convertHeader(it) })
        })
      .flatMap(
        {
          it.rxBleConnection.setupIndication(UUID.fromString(BluetoothConstants.LOCK_NOTIFICATION_CHARACTERISTIC))
        },
        { connectionData, lockIndications ->
          connectionData.apply { this.lockIndications = lockIndications.map { convertHeader(it) } }
        })
      .doOnError {
        Timber.e(
          it,
          "Connect lock: Error in connection wrapper to ${serialNumber}, Error: ${it.message}, Cause: ${it.cause}"
        )
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { onConnected(it) },
        { onConnectionError(it) }
      ).addTo(compositeDisposable)
  }

  private fun convertHeader(byteArray: ByteArray): ByteArray {
    Timber.d(
      "Lock $serialNumber current message number = ${
        (byteArray.first()
          .toInt() shr (4)) + 8
      }"
    )
    return byteArray.apply { this[0] = first() and 0xF }
  }

  private fun onConnected(connectionData: ConnectionData) {
    rxBleConnection = connectionData.rxBleConnection
    secureEstablishNotifications = connectionData.secureEstablishNotifications
    lockInteractor = LockBluetoothApiInteractor(connectionData.rxBleConnection)
    connectionData.lockIndications
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { onIndicationCharacteristic(it) },
        { Timber.e(it, "error in indications") }
      ).addTo(compositeDisposable)
    establishSecureConnection()
  }

  private fun onConnectionError(it: Throwable) {
    Timber.e(it, "on connection error in wrapper")
    wrapperListener.onConnectionChanged(
      false,
      isSecure = true
    )
  }

  private fun establishSecureConnection() {
    lockInteractor?.let { interactor ->
      secureConnectionHelper?.closeConnection()
      Timber.i("Connect lock: ${serialNumber}: all characteristics discovered, establishing secure connection from wrapper")
      secureConnectionHelper = SecureConnectionHelper(
        accessCertificate,
        interactor,
        secureEstablishNotifications,
        { Timber.e("No signed time") },
        { session ->
          this.session = session
          interactor.setSecureSession(session)
          interactor.disableNotifications()
          Timber.i("Connect lock: ${serialNumber}: secure connection is established in wrapper")
          wrapperListener.onConnectionChanged(true, isSecure = true)
        },
        { wrapperListener.onConnectionChanged(false, isSecure = true) },
        { Timber.e("Alert code not registered") },
      )
    }
  }

  private fun onIndicationCharacteristic(incomingMessage: ByteArray) {
    if (incomingMessage.first() == SecureConnectionConstants.DATA_ENCRYPTED) {
      session?.read(
        SecurityData(incomingMessage.copyOfRange(1, incomingMessage.size),
          { byteArray ->
            byteArray?.also { message ->
              Timber.i("MESSAGE: ${message.print()}")
              wrapperListener.onIndicationChanged(message)
            }
          })
      )
    } else if (incomingMessage.first() == SecureConnectionConstants.DATA_NOT_ENCRYPTED) {
      val message = incomingMessage.copyOfRange(1, incomingMessage.size)
      Timber.i("MESSAGE: ${message.print()}")
      wrapperListener.onIndicationChanged(message)
    }
  }

  fun closeConnection(notifyListener: Boolean = false, reason: String? = null) {
    Timber.w("Closing BT lock connection for $serialNumber")
    lockInteractor?.close()
    compositeDisposable.clear()
    if (notifyListener) {
      wrapperListener.onConnectionChanged(false, isSecure = true)
    }
    secureConnectionHelper?.closeConnection(reason)
    secureConnectionHelper = null
    session = null
  }

  fun openLock() = lockInteractor?.openLock()

  private data class ConnectionData(
    val rxBleConnection: RxBleConnection,
    val secureEstablishNotifications: Observable<ByteArray>,
  ) {
    lateinit var lockIndications: Observable<ByteArray>
  }
}
