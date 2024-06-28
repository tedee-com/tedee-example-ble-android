package tedee.mobile.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.polidea.rxandroidble2.exceptions.BleException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.launch
import tedee.mobile.demo.databinding.ActivityMainBinding
import tedee.mobile.demo.helper.UiSetupHelper
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError
import tedee.mobile.sdk.ble.extentions.getReadableLockCommandResult
import tedee.mobile.sdk.ble.extentions.getReadableLockNotification
import tedee.mobile.sdk.ble.extentions.getReadableLockState
import tedee.mobile.sdk.ble.extentions.getReadableLockStatusResult
import tedee.mobile.sdk.ble.extentions.getReadableStatus
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import timber.log.Timber

class MainActivity : AppCompatActivity(),
  ILockConnectionListener {

  private lateinit var binding: ActivityMainBinding
  private val lockConnectionManager by lazy { LockConnectionManager(this) }
  private val uiSetupHelper: UiSetupHelper by lazy {
    UiSetupHelper(this.applicationContext, binding, lifecycleScope, this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    RxJavaPlugins.setErrorHandler { throwable ->
      if (throwable is UndeliverableException && throwable.cause is BleException) {
        return@setErrorHandler // ignore BleExceptions since we do not have subscriber
      } else {
        throw throwable
      }
    }
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    lockConnectionManager.signedDateTimeProvider = SignedTimeProvider(lifecycleScope, uiSetupHelper)
    uiSetupHelper.setup()
    uiSetupHelper.setupSecureConnectClickListener(lockConnectionManager::connect)
    uiSetupHelper.setupDisconnectClickListener(lockConnectionManager::disconnect)
    uiSetupHelper.setupSendCommandClickListener { message, params ->
      lifecycleScope.launch {
        try {
          val result = lockConnectionManager.sendCommand(message, params)
          val readableResult = result?.getReadableLockCommandResult()
          uiSetupHelper.addMessage("Result: $readableResult")
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }

      }
    }
    uiSetupHelper.setupGetLockStateClickListener {
      lifecycleScope.launch {
        try {
          val response = lockConnectionManager.getLockState()
          val readableLockStatus = response?.getReadableLockStatusResult()
          uiSetupHelper.addMessage("getState result: \n$readableLockStatus")
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupOpenLockClickListener {
      lifecycleScope.launch {
        try {
          lockConnectionManager.openLock()
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupCloseLockClickListener {
      lifecycleScope.launch {
        try {
          lockConnectionManager.closeLock()
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupPullLockClickListener {
      lifecycleScope.launch {
        try {
          lockConnectionManager.pullSpring()
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupSetSignedTimeClickListener { time ->
      lifecycleScope.launch {
        try {
          lockConnectionManager.setSignedTime(time)
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupGetDeviceSettingsClickListener(lockConnectionManager::getDeviceSettings)
    uiSetupHelper.setupGetFirmwareVersionClickListener(lockConnectionManager::getFirmwareVersion)
    binding.buttonNavigateToAddDevice.setOnClickListener {
      val intent = Intent(this@MainActivity, RegisterLockExampleActivity::class.java)
      startActivity(intent)
      finish()
    }
  }

  override fun onLockConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
    Timber.w("LOCK LISTENER: secure connection changed: isConnected: $isConnected")
    uiSetupHelper.setCommandsSectionVisibility(false)
    uiSetupHelper.setAddingDeviceSectionVisibility(isVisible = false, isSecureConnected = true)
    when {
      isConnecting -> uiSetupHelper.changeConnectingState("Connecting...", Color.WHITE)

      isConnected -> {
        uiSetupHelper.changeConnectingState("Secure session established", Color.GREEN)
        uiSetupHelper.setCommandsSectionVisibility(true)
        uiSetupHelper.setAddingDeviceSectionVisibility(isVisible = true, isSecureConnected = true)
      }

      else -> uiSetupHelper.changeConnectingState("Disconnected", Color.RED)
    }
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
    when (throwable) {
      is DeviceNeedsResetError -> {
        Timber.d("onDeviceNeedFactoryReset called, make factory reset of the device")
        uiSetupHelper.changeConnectingState("Need factory reset", Color.RED)
        Toast.makeText(this, "Make factory reset", Toast.LENGTH_SHORT).show()
      }
      else -> {
        Timber.e(throwable, "LOCK LISTENER:: error $throwable")
        uiSetupHelper.changeConnectingState("Disconnected", Color.RED)
        val errorMessage = "Error: ${throwable.javaClass.simpleName}"
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        uiSetupHelper.addMessage(errorMessage)
      }
    }
  }

  override fun onDestroy() {
    lockConnectionManager.clear()
    super.onDestroy()
  }
}
