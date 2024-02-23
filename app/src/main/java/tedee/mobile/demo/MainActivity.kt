package tedee.mobile.demo

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import tedee.mobile.ble.example.databinding.ActivityMainBinding
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.extentions.getLockStatus
import tedee.mobile.sdk.ble.extentions.getReadableLockCommandResult
import tedee.mobile.sdk.ble.extentions.getReadableLockNotification
import tedee.mobile.sdk.ble.extentions.getReadableLockState
import tedee.mobile.sdk.ble.extentions.parseHexStringToByte
import tedee.mobile.sdk.ble.extentions.parseHexStringToByteArray
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.keystore.checkPublicKey
import tedee.mobile.sdk.ble.model.DeviceCertificate
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import timber.log.Timber

class MainActivity : AppCompatActivity(), ILockConnectionListener {

  private lateinit var binding: ActivityMainBinding

  private val lockConnectionManager by lazy { LockConnectionManager(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.serialNumber.text = SpannableStringBuilder("LOCK: $LOCK_SERIAL")
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    binding.buttonConnect.setOnClickListener {
      connectLock()
    }
    binding.buttonDisconnect.setOnClickListener {
      lockConnectionManager.disconnect()
    }
    binding.buttonSendCommand.setOnClickListener {
      val message = parseHexStringToByte(binding.editTextCommand.text.toString())
      val params = parseHexStringToByteArray(binding.editTextParam.text.toString())
      message?.let { lockConnectionManager.sendCommand(it, params) }
    }
    checkPublicKey()
  }

  private fun connectLock() {
    lockConnectionManager.connect(
      serialNumber = LOCK_SERIAL,
      deviceCertificate = DeviceCertificate(CERTIFICATE, DEVICE_PUBLIC_KEY),
      keepConnection = true,
      listener = this
    )
    resetCommandResults()
    changeConnectingState("Connecting")
  }

  override fun onDestroy() {
    lockConnectionManager.clear()
    super.onDestroy()
  }

  private fun changeConnectingState(state: String) {
    binding.connectingState.text = "State: $state"
  }

  override fun onConnectionChanged(connected: Boolean) {
    Timber.w("LOCK LISTENER: connection changed: isConnected: $connected")
    binding.clCommands.visibility = View.GONE
    resetCommandResults()
    when {
      connected -> {
        changeConnectingState("Secure session established")
        binding.clCommands.visibility = View.VISIBLE
      }

      else -> changeConnectingState("Disconnected")
    }
  }

  override fun onIndication(message: ByteArray) {
    Timber.d("LOCK LISTENER message: ${message.print()}")
    val readableResult = message.getReadableLockCommandResult()
    val formattedText = "onIndication: \nResult: $readableResult"
    binding.commandResult.text = formattedText
  }

  override fun onNotification(message: ByteArray) {
    if (message.isEmpty()) return
    Timber.d("LOCK LISTENER: notification: ${message.print()}")
    val readableNotification = message.getReadableLockNotification()
    val readableState = if (message.size > 1) message[1].getReadableLockState() else return
    val formattedText = "onNotification: \n$readableNotification " +
        "\nCurrent state: $readableState \nStatus: ${message[2].getLockStatus()}"
    binding.notification.text = formattedText
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK LISTENER:: error $throwable")
    changeConnectingState("Disconnected")
    Toast.makeText(this, "Error: ${throwable.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    resetCommandResults()
  }

  private fun resetCommandResults() {
    binding.commandResult.text = ""
    binding.notification.text = ""
  }
}
