package tedee.mobile.demo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
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
    // Switch from splash theme to normal theme
    setTheme(R.style.Theme_TedeeDemo_NoActionBar)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Check if Bluetooth is enabled
    val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
      Toast.makeText(
        this,
        "Bluetooth is disabled. Please enable Bluetooth to use this app.",
        Toast.LENGTH_LONG
      ).show()
      Timber.w("Bluetooth is disabled or not available")
    }

    RxJavaPlugins.setErrorHandler { throwable ->
      if (throwable is UndeliverableException && throwable.cause is BleException) {
        return@setErrorHandler // ignore BleExceptions since we do not have subscriber
      } else {
        throw throwable
      }
    }
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)

    // Request notification permission for Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }

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
          // Use direct BLE command 0x51 for cylinder unlock
          val result = lockConnectionManager.sendCommand(0x51.toByte())
          uiSetupHelper.addMessage("Open lock command sent: ${result?.print()}")
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupCloseLockClickListener {
      lifecycleScope.launch {
        try {
          // Use direct BLE command 0x50 for cylinder lock
          val result = lockConnectionManager.sendCommand(0x50.toByte())
          uiSetupHelper.addMessage("Close lock command sent: ${result?.print()}")
        } catch (e: Exception) {
          uiSetupHelper.onFailureRequest(e)
        }
      }
    }
    uiSetupHelper.setupPullLockClickListener {
      lifecycleScope.launch {
        try {
          // Use direct BLE command 0x52 for pull spring
          val result = lockConnectionManager.sendCommand(0x52.toByte())
          uiSetupHelper.addMessage("Pull spring command sent: ${result?.print()}")
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
    uiSetupHelper.setupDownloadActivityLogsClickListener(lockConnectionManager::sendCommand)
    uiSetupHelper.setupGetBatteryClickListener(lockConnectionManager::sendCommand)
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

    // Detailed hex dump for debugging
    val hexBytes = message.joinToString(" ") { byte -> "0x%02X".format(byte) }
    Timber.d("LOCK LISTENER: notification bytes: $hexBytes")

    // Check for HAS_ACTIVITY_LOGS notification (0xA5)
    val firstByte = message.first()
    val formattedText = when {
      firstByte == 0xA5.toByte() -> {
        """
        📋 HAS_ACTIVITY_LOGS (0xA5)

        Activity logs are ready to be collected from the lock.
        You can download them using the GET_LOGS_TLV command (0x2D).

        - Triggered after connection
        - Indicates logs waiting to download
        """.trimIndent()
      }
      else -> {
        val readableNotification = message.getReadableLockNotification()

        // Add detailed info for unknown notifications
        if (readableNotification.contains("unknown", ignoreCase = true)) {
          val firstByteHex = "0x%02X".format(firstByte.toInt() and 0xFF)
          val secondByteInfo = if (message.size > 1) {
            val secondByte = message[1]
            val secondByteHex = "0x%02X".format(secondByte.toInt() and 0xFF)
            "$secondByte ($secondByteHex)"
          } else {
            "N/A"
          }
          """
          onNotification: $readableNotification

          DEBUG INFO:
          - First byte (command): $firstByte ($firstByteHex)
          - Second byte (status): $secondByteInfo
          - Total bytes: ${message.size}
          - Full hex: $hexBytes
          """.trimIndent()
        } else {
          "onNotification: \n$readableNotification"
        }
      }
    }

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
