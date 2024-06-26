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
import tedee.mobile.sdk.ble.BluetoothConstants
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
  private var DEVICE_NAME = "SDK"
  private var DEVICE_ID: Int = 123456
  private var SERIAL_NUMBER = ""
  private val ACTIVATION_CODE = "201502XVfSdZze"
  private val mobileService by lazy { MobileService() }
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityRegisterLockExampleBinding.inflate(layoutInflater)
    setContentView(binding.root)
    Timber.plant(Timber.DebugTree())
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    lifecycleScope.launch {
      SERIAL_NUMBER = getSerialNumber(PRESET_ACTIVATION_CODE)
      addLockConnectionManager.connectForAdding(SERIAL_NUMBER, false, this@RegisterLockExampleActivity)
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
        Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
        return@launch
      }
    }
  }

  private fun registerLock() {
    lifecycleScope.launch {
      try {
        val createDoorLockData =
          addLockConnectionManager.getAddLockData(ACTIVATION_CODE, SERIAL_NUMBER)
        val updatedDoorLockData = updateCreateDoorLockData(createDoorLockData)
        val newDoorLockResponse = createNewDoorLock(updatedDoorLockData)
        val registerDeviceData =
          RegisterDeviceData(newDoorLockResponse.id, newDoorLockResponse.authPublicKey)
        DEVICE_ID = registerDeviceData.id
        addLockConnectionManager.registerDevice(registerDeviceData)
      } catch (e: Exception) {
        Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
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
    return createDoorLockData.copy(name = DEVICE_NAME)
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
    if (message.first() == BluetoothConstants.NOTIFICATION_SIGNED_DATETIME) {
      if (message.component2() == BluetoothConstants.API_RESULT_SUCCESS) {
        registerLock()
      }
    }
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK LISTENER:: error $throwable")
  }

  override fun onDestroy() {
    addLockConnectionManager.clear()
    super.onDestroy()
  }
}
