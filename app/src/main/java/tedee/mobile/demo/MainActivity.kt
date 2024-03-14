package tedee.mobile.demo

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import tedee.mobile.demo.databinding.ActivityMainBinding
import tedee.mobile.demo.helper.UiSetupHelper
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.extentions.getReadableLockCommandResult
import tedee.mobile.sdk.ble.extentions.getReadableLockNotification
import tedee.mobile.sdk.ble.extentions.getReadableLockState
import tedee.mobile.sdk.ble.extentions.getReadableLockStatusResult
import tedee.mobile.sdk.ble.extentions.getReadableStatus
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import timber.log.Timber


class MainActivity : AppCompatActivity(), ILockConnectionListener {

  private lateinit var binding: ActivityMainBinding
  private val lockConnectionManager by lazy { LockConnectionManager(this) }
  private val uiSetupHelper: UiSetupHelper by lazy {
    UiSetupHelper(this.applicationContext, binding, lifecycleScope, this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    Timber.plant(Timber.DebugTree())
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    lockConnectionManager.signedDateTimeProvider = SignedTimeProvider(lifecycleScope, uiSetupHelper)
    uiSetupHelper.setup()
    uiSetupHelper.setupConnectClickListener(lockConnectionManager::connect)
    uiSetupHelper.setupDisconnectClickListener(lockConnectionManager::disconnect)
    uiSetupHelper.setupSendCommandClickListener(lockConnectionManager::sendCommand)
    uiSetupHelper.setupGetLockStateClickListener(lockConnectionManager::getLockState)
    uiSetupHelper.setupOpenLockClickListener(lockConnectionManager::openLock)
    uiSetupHelper.setupCloseLockClickListener(lockConnectionManager::closeLock)
    uiSetupHelper.setupPullLockClickListener(lockConnectionManager::pullSpring)
    uiSetupHelper.setupSetSignedTimeClickListener(lockConnectionManager::setSignedTime)
  }

  override fun onConnectionChanged(isConnecting: Boolean, connected: Boolean) {
    Timber.w("LOCK LISTENER: connection changed: isConnected: $connected")
    uiSetupHelper.setCommandsSectionVisibility(false)
    when {
      isConnecting -> uiSetupHelper.changeConnectingState("Connecting...", Color.WHITE)

      connected -> {
        uiSetupHelper.changeConnectingState("Secure session established", Color.GREEN)
        uiSetupHelper.setCommandsSectionVisibility(true)
      }

      else -> uiSetupHelper.changeConnectingState("Disconnected", Color.RED)
    }
  }

  @SuppressLint("SetTextI18n")
  override fun onIndication(message: ByteArray) {
    Timber.d("LOCK LISTENER message: ${message.print()}")
    val formattedText = when (message.first()) {
      BluetoothConstants.GET_STATE -> {
        val readableLockStatus = message.getReadableLockStatusResult()
        "getState result: \n$readableLockStatus"
      }

      else -> {
        val readableResult = message.getReadableLockCommandResult()
        "onIndication: \nResult: $readableResult"
      }
    }
    uiSetupHelper.addMessage(formattedText)
  }

  @SuppressLint("SetTextI18n")
  override fun onNotification(message: ByteArray) {
    if (message.isEmpty()) return
    Timber.d("LOCK LISTENER: notification: ${message.print()}")
    val readableNotification = message.getReadableLockNotification()
    val formattedText = "onNotification: \n$readableNotification"
    uiSetupHelper.addMessage(formattedText)
  }

  override fun onLockStatusChanged(currentState: Byte, status: Byte) {
    Timber.d("LOCK LISTENER: onLockStatusChange: currentState = $currentState, operation status = $status")
    val readableState = currentState.getReadableLockState()
    val readableStatus = status.getReadableStatus()
    val formattedText =
      "onLockStatusChange: \nCurrent state: $readableState \nStatus: $readableStatus"
    uiSetupHelper.addMessage(formattedText)
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK LISTENER:: error $throwable")
    uiSetupHelper.changeConnectingState("Disconnected", Color.RED)
    val errorMessage = "Error: ${throwable.javaClass.simpleName}"
    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    uiSetupHelper.addMessage(errorMessage)
  }

  override fun onDestroy() {
    lockConnectionManager.clear()
    super.onDestroy()
  }
}
