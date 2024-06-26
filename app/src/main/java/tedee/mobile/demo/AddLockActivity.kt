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
import tedee.mobile.demo.databinding.ActivityAddBinding
import tedee.mobile.demo.helper.UiAddLockSetupHelper
import tedee.mobile.sdk.ble.bluetooth.adding.IAddLockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.adding.AddLockConnectionManager
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError
import tedee.mobile.sdk.ble.extentions.getReadableLockNotification
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import timber.log.Timber

class AddLockActivity : AppCompatActivity(),
  IAddLockConnectionListener {

  private lateinit var binding: ActivityAddBinding
  private val lockConnectionManager by lazy { AddLockConnectionManager(this) }
  private val uiSetupHelper: UiAddLockSetupHelper by lazy {
    UiAddLockSetupHelper(this.applicationContext, binding, lifecycleScope, this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityAddBinding.inflate(layoutInflater)
    setContentView(binding.root)
    RxJavaPlugins.setErrorHandler { throwable ->
      if (throwable is UndeliverableException && throwable.cause is BleException) {
        return@setErrorHandler // ignore BleExceptions since we do not have subscriber
      } else {
        throw throwable
      }
    }
    Timber.plant(Timber.DebugTree())
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    uiSetupHelper.apply {
      setup()
      setupAddLockConnectClickListener(lockConnectionManager::connectForAdding)
      setupDisconnectClickListener(lockConnectionManager::disconnect)
      setupGetDeviceSettingsClickListener(lockConnectionManager::getUnsecureDeviceSettings)
      setupGetFirmwareVersionClickListener(lockConnectionManager::getUnsecureFirmwareVersion)
      setupGetSignatureClickListener { lockConnectionManager.getSignature() }
      setupRegisterDeviceClickListener(lockConnectionManager::getAddLockData, lockConnectionManager::registerDevice)
      setupSetSignedTimeClickListener { time ->
        lifecycleScope.launch {
          try {
            lockConnectionManager.setSignedTime(time)
          } catch (e: Exception) {
            uiSetupHelper.onFailureRequest(e)
          }
        }
      }
      binding.buttonNavigateToMain.setOnClickListener {
        val intent = Intent(this@AddLockActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
      }
    }
  }

  override fun onUnsecureConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
    Timber.w("LOCK LISTENER: add lock connection changed: isConnected: $isConnected")
    uiSetupHelper.setAddingDeviceSectionVisibility(isVisible = false, isSecureConnected = false)
    when {
      isConnecting -> uiSetupHelper.changeConnectingState("Connecting...", Color.WHITE)

      isConnected -> {
        uiSetupHelper.changeConnectingState("Add lock connection established", Color.GREEN)
        uiSetupHelper.setAddingDeviceSectionVisibility(isVisible = true, isSecureConnected = false)
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
