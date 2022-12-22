package tedee.mobile.demo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import tedee.mobile.demo.bluetooth.BluetoothConnectionWrapper
import tedee.mobile.demo.bluetooth.BluetoothWrapperListener
import tedee.mobile.demo.bluetooth.extractSerialNumber
import tedee.mobile.demo.databinding.ActivityMainBinding
import tedee.mobile.demo.extentions.print
import tedee.mobile.demo.keystore.KeyStoreHelper
import tedee.mobile.demo.model.DeviceCertificate
import timber.log.Timber
import java.util.*

private const val CERTIFICATE: String = "TODO"
private const val DEVICE_PUBLIC_KEY: String = "TODO"
private const val LOCK_SERIAL: String = "TODO"

class MainActivity : AppCompatActivity(), BluetoothWrapperListener {

  private lateinit var binding: ActivityMainBinding
  private lateinit var rxBleClient: RxBleClient

  private var connectionWrapper: BluetoothConnectionWrapper? = null
  private var disposable: CompositeDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.plant(Timber.DebugTree())
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.serialNumber.text = SpannableStringBuilder("LOCK: $LOCK_SERIAL")
    binding.buttonConnect.setOnClickListener { connectToLockTemporary(LOCK_SERIAL) }
    binding.buttonUnlock.setOnClickListener { connectionWrapper?.openLock() }
    rxBleClient = RxBleClient.create(this)
    checkPublicKey()
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
  }

  private fun checkPublicKey() {
    val publicKey = KeyStoreHelper.getMobilePublicKey()?.replace("\n", "\\n")
    if (publicKey == null) {
      KeyStoreHelper.generateMobileKeyPair(
        { Timber.w("!!! Public key to register mobile:\n ${it.replace("\n", "\\n")}") },
        { Timber.e("!!! Cannot generate key pair") }
      )
    } else {
      Timber.w("!!! Public key to register mobile and get certificate:\n $publicKey")
    }
  }

  override fun onDestroy() {
    connectionWrapper?.closeConnection()
    disposable.clear()
    super.onDestroy()
  }

  private fun getBluetoothPermissions() =
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        mutableListOf(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.BLUETOOTH_CONNECT
        )
      }
      else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

  private fun connectToLockTemporary(serialNumber: String) {
    connectionWrapper?.closeConnection()
    disposable.clear()
    disposable.add(rxBleClient.observeStateChanges()
      .startWith(rxBleClient.state)
      .distinctUntilChanged()
      .filter { it == RxBleClient.State.READY }
      .flatMapSingle {
        rxBleClient
          .scanBleDevices(
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            ScanFilter.Builder()
              .setServiceUuid(ParcelUuid(UUID.fromString(BluetoothConstants.LOCK_SERVICE_UUID)))
              .build()
          )
          .filter { scanResult ->
            val bleDeviceSerialNumber =
              extractSerialNumber(
                scanResult.scanRecord.serviceUuids?.map { it.toString() },
                BluetoothConstants.LOCK_SERVICE_UUID
              )
            serialNumber.equals(bleDeviceSerialNumber, true)
          }
          .map { it.bleDevice }
          .firstOrError()
      }
      .firstOrError()
      .doOnSubscribe { changeConnectingState("Scanning") }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        {
          changeConnectingState("Connecting")
          connectionWrapper?.closeConnection()
          connectionWrapper = BluetoothConnectionWrapper(
            serialNumber,
            it,
            DeviceCertificate(CERTIFICATE, DEVICE_PUBLIC_KEY),
            this
          ).also { wrapper -> wrapper.connect() }
        },
        { onError(it) }
      )
    )
  }

  override fun onConnectionChanged(connected: Boolean, isSecure: Boolean) {
    Timber.d("LOCK: connection changed: isConnected: $connected, isSecure: $isSecure")
    binding.buttonUnlock.visibility = View.GONE
    when {
      connected && isSecure -> {
        changeConnectingState("Secure session established")
        binding.buttonUnlock.visibility = View.VISIBLE
      }
      connected && !isSecure -> changeConnectingState("Connected via BT")
      else -> changeConnectingState("Disconnected")
    }
  }

  override fun onIndicationChanged(message: ByteArray) {
    val readableMessage = message.print()
    Timber.d("LOCK: message: $readableMessage")
    Toast.makeText(this, readableMessage, Toast.LENGTH_SHORT).show()
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK: error")
    changeConnectingState("Disconnected")
    Toast.makeText(this, "Error: ${throwable.localizedMessage}", Toast.LENGTH_SHORT).show()
  }

  private fun changeConnectingState(state: String) {
    binding.connectingState.text = state
  }
}
