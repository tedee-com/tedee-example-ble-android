package tedee.mobile.demo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.polidea.rxandroidble2.exceptions.BleException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
  private var batteryRefreshJob: Job? = null
  private var isConnected = false

  companion object {
    private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 9
    private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 10
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    // Switch from splash theme to normal theme BEFORE super.onCreate()
    setTheme(R.style.Theme_TedeeDemo_NoActionBar)
    super.onCreate(savedInstanceState)

    Timber.d("MainActivity onCreate started")
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    Timber.d("Content view set successfully")

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
    } else {
      Timber.d("Bluetooth is enabled and available")
    }

    RxJavaPlugins.setErrorHandler { throwable ->
      if (throwable is UndeliverableException && throwable.cause is BleException) {
        return@setErrorHandler // ignore BleExceptions since we do not have subscriber
      } else {
        throw throwable
      }
    }

    // Request permissions sequentially
    if (!hasBluetoothPermissions()) {
      requestPermissions(getBluetoothPermissions().toTypedArray(), BLUETOOTH_PERMISSION_REQUEST_CODE)
    } else {
      // If Bluetooth permissions already granted, request notification permission
      requestNotificationPermissionIfNeeded()
    }

    lockConnectionManager.signedDateTimeProvider = SignedTimeProvider(lifecycleScope, uiSetupHelper)
    uiSetupHelper.setup()
    uiSetupHelper.setupSecureConnectClickListener { serialNumber, deviceCertificate, keepConnection, listener ->
      if (!hasBluetoothPermissions()) {
        Toast.makeText(
          this,
          "Please grant Bluetooth permissions first",
          Toast.LENGTH_LONG
        ).show()
        requestPermissions(getBluetoothPermissions().toTypedArray(), BLUETOOTH_PERMISSION_REQUEST_CODE)
        return@setupSecureConnectClickListener
      }
      lockConnectionManager.connect(serialNumber, deviceCertificate, keepConnection, listener)
    }
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

    Timber.d("MainActivity onCreate completed successfully")
  }

  override fun onLockConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
    Timber.w("LOCK LISTENER: secure connection changed: isConnected: $isConnected")
    this.isConnected = isConnected
    uiSetupHelper.setCommandsSectionVisibility(false)
    uiSetupHelper.setAddingDeviceSectionVisibility(isVisible = false, isSecureConnected = true)
    when {
      isConnecting -> {
        uiSetupHelper.changeConnectingState("Connecting...", Color.WHITE)
        stopBatteryRefresh()
      }

      isConnected -> {
        uiSetupHelper.changeConnectingState("Secure session established", Color.GREEN)
        uiSetupHelper.setCommandsSectionVisibility(true)
        uiSetupHelper.setAddingDeviceSectionVisibility(isVisible = true, isSecureConnected = true)
        // Start battery refresh when connected
        startBatteryRefresh()
      }

      else -> {
        uiSetupHelper.changeConnectingState("Disconnected", Color.RED)
        stopBatteryRefresh()
      }
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

  private fun hasBluetoothPermissions(): Boolean {
    val bluetoothPermissions = getBluetoothPermissions()
    return bluetoothPermissions.all { permission ->
      ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
      if (ContextCompat.checkSelfPermission(this, notificationPermission) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(notificationPermission), NOTIFICATION_PERMISSION_REQUEST_CODE)
      }
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      BLUETOOTH_PERMISSION_REQUEST_CODE -> {
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
          Timber.d("Bluetooth permissions granted")
          // Request notification permission after Bluetooth permissions
          requestNotificationPermissionIfNeeded()
        } else {
          Timber.w("Bluetooth permissions denied")
          Toast.makeText(
            this,
            "Bluetooth permissions are required for BLE communication",
            Toast.LENGTH_LONG
          ).show()
        }
      }
      NOTIFICATION_PERMISSION_REQUEST_CODE -> {
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
          Timber.d("Notification permission granted")
        } else {
          Timber.w("Notification permission denied")
        }
      }
    }
  }

  private fun startBatteryRefresh() {
    Timber.d("Starting battery refresh")
    // Cancel any existing job
    batteryRefreshJob?.cancel()

    // Start new refresh job
    batteryRefreshJob = lifecycleScope.launch {
      // First update immediately
      try {
        updateBatteryLevel()
      } catch (e: Exception) {
        Timber.e(e, "Error in initial battery update")
      }

      // Then continue updating every 30 seconds
      while (isActive && isConnected) {
        try {
          delay(30000) // Wait 30 seconds
          updateBatteryLevel()
        } catch (e: Exception) {
          Timber.e(e, "Error refreshing battery")
          delay(30000) // Continue trying even after error
        }
      }
      Timber.d("Battery refresh loop ended")
    }
  }

  private fun stopBatteryRefresh() {
    batteryRefreshJob?.cancel()
    batteryRefreshJob = null
    binding.batteryLevel.visibility = android.view.View.GONE
  }

  @SuppressLint("SetTextI18n")
  private suspend fun updateBatteryLevel() {
    try {
      Timber.d("Requesting battery level...")
      val response = lockConnectionManager.sendCommand(0x0C.toByte(), null)

      Timber.d("Battery response: ${response?.print() ?: "null"}, size: ${response?.size ?: 0}")

      if (response != null && response.size >= 4) {
        val batteryLevel = response[2].toInt() and 0xFF
        val chargingStatus = response[3].toInt() and 0xFF
        val chargingIcon = if (chargingStatus == 1) "⚡" else "🔋"

        // lifecycleScope already runs on main thread, no need for runOnUiThread
        binding.batteryLevel.text = "$chargingIcon Battery: $batteryLevel%"
        binding.batteryLevel.visibility = android.view.View.VISIBLE
        Timber.d("Battery updated successfully: $batteryLevel% charging=$chargingStatus")
      } else {
        Timber.w("Battery response is null or too short: ${response?.size ?: 0} bytes")
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to update battery level")
      // Don't show battery if there's an error, keep it hidden
    }
  }

  override fun onDestroy() {
    stopBatteryRefresh()
    lockConnectionManager.clear()
    super.onDestroy()
  }
}
