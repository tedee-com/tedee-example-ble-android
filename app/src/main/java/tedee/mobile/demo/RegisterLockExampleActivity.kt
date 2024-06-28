package tedee.mobile.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tedee.mobile.demo.api.data.model.NewDoorLockResponse
import tedee.mobile.demo.api.service.MobileService
import tedee.mobile.demo.databinding.ActivityRegisterLockExampleBinding
import tedee.mobile.sdk.ble.BluetoothConstants.API_RESULT_SUCCESS
import tedee.mobile.sdk.ble.BluetoothConstants.NOTIFICATION_SIGNED_DATETIME
import tedee.mobile.sdk.ble.bluetooth.adding.AddLockConnectionManager
import tedee.mobile.sdk.ble.bluetooth.adding.IAddLockConnectionListener
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.model.CreateDoorLockData
import tedee.mobile.sdk.ble.model.RegisterDeviceData
import tedee.mobile.sdk.ble.model.SignedTime
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import timber.log.Timber

class RegisterLockExampleActivity : AppCompatActivity(), IAddLockConnectionListener {

  private lateinit var binding: ActivityRegisterLockExampleBinding
  private var deviceName = "Lock from SDK"
  private var deviceId: Int = -1
  private var serialNumber = ""
  private var activationCode = PRESET_ACTIVATION_CODE
  private val mobileService by lazy { MobileService() }
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityRegisterLockExampleBinding.inflate(layoutInflater)
    setContentView(binding.root)
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    lifecycleScope.launch {
      try {
        serialNumber = getSerialNumber(activationCode)
        Toast.makeText(applicationContext, "Serial number: $serialNumber", Toast.LENGTH_SHORT)
          .show()
        addLockConnectionManager.connectForAdding(
          serialNumber,
          false,
          this@RegisterLockExampleActivity
        )
      } catch (e: Exception) {
        showErrorToast(e)
      }
    }
    binding.buttonNavigateToMain.setOnClickListener {
      val intent = Intent(this@RegisterLockExampleActivity, MainActivity::class.java)
      startActivity(intent)
      finish()
    }
  }

  private fun setSignedDateTime() {
    lifecycleScope.launch {
      try {
        val signedTime = getSignedTime()
        addLockConnectionManager.setSignedTime(signedTime)
      } catch (e: Exception) {
        showErrorToast(e)
        return@launch
      }
    }
  }

  private fun registerLock() {
    lifecycleScope.launch {
      try {
        val createDoorLockData =
          addLockConnectionManager.getAddLockData(activationCode, serialNumber)
        val updatedDoorLockData = updateCreateDoorLockData(createDoorLockData)
        val newDoorLockResponse = createNewDoorLock(updatedDoorLockData)
        val registerDeviceData =
          RegisterDeviceData(newDoorLockResponse.id, newDoorLockResponse.authPublicKey)
        deviceId = registerDeviceData.id
        addLockConnectionManager.registerDevice(registerDeviceData)
        Toast.makeText(
          applicationContext,
          "Lock was added: DEVICE_ID: $deviceId, DEVICE_NAME: $deviceName",
          Toast.LENGTH_SHORT
        ).show()
      } catch (e: Exception) {
        showErrorToast(e)
        return@launch
      }
    }
  }

  private suspend fun getSerialNumber(activationCode: String): String {
    return try {
      mobileService.getSerialNumber(activationCode)
    } catch (error: Exception) {
      throw error
    }
  }

  private suspend fun getSignedTime(): SignedTime {
    return try {
      mobileService.getSignedTime()
    } catch (error: Exception) {
      throw error
    }
  }

  private fun updateCreateDoorLockData(createDoorLockData: CreateDoorLockData): CreateDoorLockData {
    return createDoorLockData.copy(name = deviceName)
  }

  private suspend fun createNewDoorLock(createDoorLockResponse: CreateDoorLockData): NewDoorLockResponse {
    return try {
      mobileService.createNewDoorLock(createDoorLockResponse)
    } catch (error: Exception) {
      throw error
    }
  }

  override fun onUnsecureConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
    when {
      isConnecting -> Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
      isConnected -> {
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
        setSignedDateTime()
      }

      else -> Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onNotification(message: ByteArray) {
    if (message.isEmpty()) return
    Timber.d("LOCK LISTENER: notification: ${message.print()}")
    if (message.first() == NOTIFICATION_SIGNED_DATETIME && message.component2() == API_RESULT_SUCCESS) {
      registerLock()
    }
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK LISTENER:: error $throwable")
  }

  private fun showErrorToast(exception: Exception) {
    Toast.makeText(
      applicationContext,
      "${exception::class.java.simpleName} ${exception.message ?: ""}",
      Toast.LENGTH_SHORT
    ).show()
  }

  override fun onDestroy() {
    addLockConnectionManager.clear()
    super.onDestroy()
  }
}