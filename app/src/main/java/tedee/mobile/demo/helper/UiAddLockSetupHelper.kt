package tedee.mobile.demo.helper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import tedee.mobile.demo.PRESET_ACTIVATION_CODE
import tedee.mobile.demo.adapter.BleResultItem
import tedee.mobile.demo.adapter.BleResultsAdapter
import tedee.mobile.demo.databinding.ActivityAddBinding
import tedee.mobile.demo.manager.CreateDoorLockManager
import tedee.mobile.demo.manager.SerialNumberManager
import tedee.mobile.demo.manager.SignedTimeManager
import tedee.mobile.sdk.ble.bluetooth.adding.IAddLockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError
import tedee.mobile.sdk.ble.model.CreateDoorLockData
import tedee.mobile.sdk.ble.model.DeviceSettings
import tedee.mobile.sdk.ble.model.FirmwareVersion
import tedee.mobile.sdk.ble.model.RegisterDeviceData
import tedee.mobile.sdk.ble.model.SignedTime
import timber.log.Timber

class UiAddLockSetupHelper(
  private val context: Context,
  private val binding: ActivityAddBinding,
  private val lifecycleScope: LifecycleCoroutineScope,
  private val addLockConnectionListener: IAddLockConnectionListener,
) : UiHelper {
  private var isSecureConnected: Boolean = false
  private val items: MutableList<BleResultItem> = mutableListOf()
  private val bleResultsAdapter: BleResultsAdapter by lazy { BleResultsAdapter() }
  private val signedTimeManager: SignedTimeManager by lazy { SignedTimeManager(lifecycleScope) }
  private val serialNumberManager: SerialNumberManager by lazy { SerialNumberManager() }
  private val createDoorLockManager: CreateDoorLockManager by lazy { CreateDoorLockManager() }

  fun setup() {
    setupRecyclerView()
    initPresetValue()
    setupGetSerialNumberButton()
  }

  fun setupAddLockConnectClickListener(
    connectLock: (
      serialNumber: String,
      keepConnection: Boolean,
      addLockConnectionListener: IAddLockConnectionListener,
    ) -> Unit,
  ) {
    binding.buttonaddLockConnect.setOnClickListener {
      val serialNumber = binding.editTextSerialNumber.text.toString()
      connectLock(serialNumber, false, addLockConnectionListener)
      changeConnectingState("Searching...")
    }
  }

  private fun setupRecyclerView() {
    binding.recyclerView.run {
      layoutManager = LinearLayoutManager(this.context)
      adapter = bleResultsAdapter
    }
  }

  fun setupDisconnectClickListener(disconnect: () -> Unit) {
    binding.buttonDisconnect.setOnClickListener { disconnect() }
  }

  fun setupGetDeviceSettingsClickListener(getDeviceSettings: suspend (Boolean) -> DeviceSettings?) {
    binding.buttonGetDeviceSettings.setOnClickListener {
      lifecycleScope.launch {
        try {
          val deviceSettings = getDeviceSettings(isSecureConnected)
          Timber.d("Device settings: $deviceSettings")
          Toast.makeText(context, "$deviceSettings", Toast.LENGTH_SHORT).show()
        } catch (e: DeviceNeedsResetError) {
          Timber.e(e, "Device settings: DeviceNeedsResetError = $e")
        } catch (e: Exception) {
          Timber.e(e, "Device settings: Other exception = $e")
        }
      }
    }
  }

  fun setupGetFirmwareVersionClickListener(getFirmwareVersion: suspend (Boolean) -> FirmwareVersion?) {
    binding.buttonGetFirmwareVersion.setOnClickListener {
      lifecycleScope.launch {
        try {
          val firmwareVersion = getFirmwareVersion(isSecureConnected)
          Timber.d("Firmware version: $firmwareVersion")
          Toast.makeText(context, "$firmwareVersion", Toast.LENGTH_SHORT).show()
        } catch (e: DeviceNeedsResetError) {
          Timber.e(e, "Firmware version: DeviceNeedsResetError = $e")
        } catch (e: Exception) {
          Timber.e(e, "Firmware version: Other exception = $e")
        }
      }
    }
  }

  fun setupGetSignatureClickListener(getSignature: suspend () -> String?) {
    binding.buttonGetSignature.setOnClickListener {
      lifecycleScope.launch {
        try {
          val signature = getSignature()
          Timber.d("Signature: $signature")
          Toast.makeText(context, "$signature", Toast.LENGTH_SHORT).show()
        } catch (e: DeviceNeedsResetError) {
          Timber.e(e, "Signature: DeviceNeedsResetError = $e")
        } catch (e: Exception) {
          Timber.e(e, "Signature: Other exception = $e")
        }
      }
    }
  }

  fun setupRegisterDeviceClickListener(
    getCreateDoorLockData: suspend (String, String) -> CreateDoorLockData,
    registerDevice: suspend (RegisterDeviceData) -> Unit,
  ) {
    binding.buttonRegisterDevice.setOnClickListener {
      lifecycleScope.launch {
        try {
          val createDoorLockData = getCreateDoorLockData(
            binding.editTextActivationCode.text.toString(),
            binding.editTextSerialNumber.text.toString()
          )
          val updatedDoorLockData = updateCreateDoorLockData(createDoorLockData)
          Timber.d("Updated Door Lock: $updatedDoorLockData")
          val newDoorLockResponse = createDoorLockManager.createNewDoorLock(updatedDoorLockData)
          Timber.d("New Door Lock Response: $newDoorLockResponse")
          val registerDeviceData =
            RegisterDeviceData(newDoorLockResponse.id, newDoorLockResponse.authPublicKey)
          registerDevice(registerDeviceData)
        } catch (e: Exception) {
          Timber.e(e, "RegisterDevice: exception = $e")
        }
      }
    }
  }

  private fun updateCreateDoorLockData(createDoorLockData: CreateDoorLockData): CreateDoorLockData {
    return createDoorLockData.copy(name = "SDK")
  }

  @SuppressLint("SetTextI18n")
  fun changeConnectingState(state: String, color: Int = Color.WHITE) {
    binding.connectingState.text = "State: $state"
    binding.connectingState.setTextColor(color)
  }


  fun setAddingDeviceSectionVisibility(isVisible: Boolean, isSecureConnected: Boolean) {
    this.isSecureConnected = isSecureConnected
    binding.clAddingDevice.isVisible = isVisible
  }

  private fun initPresetValue() {
    PRESET_ACTIVATION_CODE.takeIf { it.isNotEmpty() }
      ?.let { binding.editTextActivationCode.setText(it) }
  }

  private fun setupGetSerialNumberButton() {
    binding.buttonGetSerialNumber.setOnClickListener {
      val activationCode = binding.editTextActivationCode.text.toString()
      if (activationCode.isNotBlank()) {
        lifecycleScope.launch {
          try {
            val serialNumber = serialNumberManager.getSerialNumber(activationCode)
            binding.editTextSerialNumber.setText(serialNumber)
          } catch (e: Exception) {
            Timber.e(e)
          }
        }
      } else {
        Toast.makeText(context, "Activation Code have to be filled", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun setupSetSignedTimeClickListener(setSignedTime: (SignedTime) -> Unit) {
    binding.buttonSetSignedTime.setOnClickListener { getAndSetSignedTime(setSignedTime) }
  }

  private fun getAndSetSignedTime(setSignedTime: (SignedTime) -> Unit) {
    signedTimeManager.getSignedTime(setSignedTime, ::onFailureRequest)
  }

  override suspend fun getSignedTime(): SignedTime = signedTimeManager.getSignedTime()

  override fun onFailureRequest(error: Throwable) {
    val message = "Error: ${error.message ?: error::class.java.simpleName}"
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    Timber.e(error, message)
  }

  fun addMessage(message: String) {
    items.add(0, BleResultItem(message))
    bleResultsAdapter.addItems(items)
    bleResultsAdapter.notifyItemInserted(0)
    binding.recyclerView.scrollToPosition(0)
  }
}
