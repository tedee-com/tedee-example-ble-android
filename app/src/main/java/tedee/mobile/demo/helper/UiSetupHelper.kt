package tedee.mobile.demo.helper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import tedee.mobile.demo.PRESET_DEVICE_ID
import tedee.mobile.demo.PRESET_NAME
import tedee.mobile.demo.PRESET_SERIAL_NUMBER
import tedee.mobile.demo.R
import tedee.mobile.demo.adapter.BleResultItem
import tedee.mobile.demo.adapter.BleResultsAdapter
import tedee.mobile.demo.api.data.model.MobileCertificateResponse
import tedee.mobile.demo.api.data.model.RegisterMobileResponse
import tedee.mobile.demo.databinding.ActivityMainBinding
import tedee.mobile.demo.manager.CertificateManager
import tedee.mobile.demo.manager.SignedTimeManager
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError
import tedee.mobile.sdk.ble.extentions.parseHexStringToByte
import tedee.mobile.sdk.ble.extentions.parseHexStringToByteArray
import tedee.mobile.sdk.ble.model.DeviceCertificate
import tedee.mobile.sdk.ble.model.DeviceSettings
import tedee.mobile.sdk.ble.model.FirmwareVersion
import tedee.mobile.sdk.ble.model.SignedTime
import timber.log.Timber

class UiSetupHelper(
  private val context: Context,
  private val binding: ActivityMainBinding,
  private val lifecycleScope: LifecycleCoroutineScope,
  private val secureConnectionListener: ILockConnectionListener
) : UiHelper {

  private val items: MutableList<BleResultItem> = mutableListOf()
  private val bleResultsAdapter: BleResultsAdapter by lazy { BleResultsAdapter() }
  private val certificateManager: CertificateManager by lazy {
    CertificateManager(context, lifecycleScope)
  }
  private val signedTimeManager: SignedTimeManager by lazy { SignedTimeManager(lifecycleScope) }
  private var openLockParam: Byte = BluetoothConstants.PARAM_NONE
  private var closeLockParam: Byte = BluetoothConstants.PARAM_NONE
  private var isSecureConnected: Boolean = false

  fun setup() {
    initPresetValues()
    setupRecyclerView()
    certificateManager.setupMobilePublicKey()
    certificateManager.setupCertificateData()
    setupGenerateCertificateButton()
    setupOpenLockParamsSpinner()
    setupCloseLockParamsSpinner()
  }

  fun setupSecureConnectClickListener(
    connectLock: (
      serialNumber: String,
      deviceCertificate: DeviceCertificate,
      keepConnection: Boolean,
      secureConnectionListener: ILockConnectionListener,
    ) -> Unit,
  ) {
    binding.buttonSecureConnect.setOnClickListener {
      val serialNumber = binding.editTextSerialNumber.text.toString()
      val keepConnection = binding.switchKeepConnection.isChecked
      val deviceCertificate =
        DeviceCertificate(certificateManager.certificate, certificateManager.devicePublicKey)
      connectLock(
        serialNumber,
        deviceCertificate,
        keepConnection,
        secureConnectionListener
      )
      changeConnectingState("Searching...")
    }
  }

  fun setupDisconnectClickListener(disconnect: () -> Unit) {
    binding.buttonDisconnect.setOnClickListener { disconnect() }
  }

  fun setupSendCommandClickListener(sendCommand: (message: Byte, params: ByteArray?) -> Unit) {
    binding.buttonSendCommand.setOnClickListener {
      val message = parseHexStringToByte(binding.editTextCommand.text.toString())
      val params = parseHexStringToByteArray(binding.editTextParam.text.toString())
      message?.let { sendCommand(it, params) }
    }
  }

  fun setupGetLockStateClickListener(getLockState: () -> Unit) {
    binding.buttonGetLockState.setOnClickListener { getLockState() }
  }

  fun setupOpenLockClickListener(openLock: (param: Byte) -> Unit) {
    binding.buttonOpenLock.setOnClickListener { openLock(openLockParam) }
  }

  fun setupCloseLockClickListener(closeLock: (param: Byte) -> Unit) {
    binding.buttonCloseLock.setOnClickListener { closeLock(closeLockParam) }
  }

  fun setupPullLockClickListener(pullLock: () -> Unit) {
    binding.buttonPullLock.setOnClickListener { pullLock() }
  }

  fun setupSetSignedTimeClickListener(setSignedTime: (SignedTime) -> Unit) {
    binding.buttonSetSignedTime.setOnClickListener { getAndSetSignedTime(setSignedTime) }
  }

  fun setupDownloadActivityLogsClickListener(sendCommand: suspend (Byte, ByteArray?) -> ByteArray?) {
    binding.buttonDownloadActivityLogs.setOnClickListener {
      lifecycleScope.launch {
        try {
          val allLogs = mutableListOf<String>()
          var packageCount = 0
          var resultCode: Byte

          addMessage("📋 Starting activity logs download...")

          // Loop to download all log packages
          do {
            packageCount++
            val response = sendCommand(0x2D.toByte(), null)

            if (response == null || response.isEmpty()) {
              addMessage("❌ No response from lock")
              return@launch
            }

            resultCode = response[0]

            when (resultCode) {
              0x00.toByte() -> {
                // SUCCESS - more logs available
                val logData = response.print()
                allLogs.add("Package $packageCount (MORE): $logData")
                Timber.d("Activity logs package $packageCount: $logData")
              }
              0x04.toByte() -> {
                // NOT_FOUND - last package or no logs
                if (response.size > 1) {
                  val logData = response.print()
                  allLogs.add("Package $packageCount (LAST): $logData")
                  Timber.d("Activity logs last package: $logData")
                } else {
                  allLogs.add("Package $packageCount: No more logs available")
                }
              }
              0x03.toByte() -> {
                // BUSY - wait and retry
                allLogs.add("Package $packageCount: Lock busy, waiting 200ms...")
                kotlinx.coroutines.delay(200)
              }
              0x02.toByte() -> {
                addMessage("❌ MTU too small (minimum 98 bytes required)")
                return@launch
              }
              0x07.toByte() -> {
                addMessage("❌ No permission to read logs")
                return@launch
              }
              else -> {
                addMessage("❌ Unknown result code: ${resultCode.toString(16)}")
                return@launch
              }
            }

            // Safety limit to prevent infinite loops
            if (packageCount >= 100) {
              allLogs.add("⚠️ Stopped after 100 packages (safety limit)")
              break
            }

          } while (resultCode != 0x04.toByte()) // Continue until NOT_FOUND

          val summary = """
            |📋 Activity Logs Downloaded
            |
            |Total packages: $packageCount
            |${allLogs.joinToString("\n")}
          """.trimMargin()

          addMessage(summary)
        } catch (e: Exception) {
          addMessage("❌ Failed to download logs: ${e.message}")
          Timber.e(e, "Error downloading activity logs")
        }
      }
    }
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

  private fun getAndSetSignedTime(setSignedTime: (SignedTime) -> Unit) {
    signedTimeManager.getSignedTime(setSignedTime, ::onFailureRequest)
  }

  override suspend fun getSignedTime(): SignedTime = signedTimeManager.getSignedTime()

  @SuppressLint("SetTextI18n")
  fun changeConnectingState(state: String, color: Int = Color.WHITE) {
    binding.connectingState.text = "State: $state"
    binding.connectingState.setTextColor(color)
  }

  fun addMessage(message: String) {
    items.add(0, BleResultItem(message))
    bleResultsAdapter.addItems(items)
    bleResultsAdapter.notifyItemInserted(0)
    binding.recyclerView.scrollToPosition(0)
  }

  fun setCommandsSectionVisibility(isVisible: Boolean) {
    binding.clCommands.isVisible = isVisible
  }

  fun setAddingDeviceSectionVisibility(isVisible: Boolean, isSecureConnected: Boolean) {
    this.isSecureConnected = isSecureConnected
    binding.clAddingDevice.isVisible = isVisible
  }

  private fun initPresetValues() {
    PRESET_DEVICE_ID.takeIf { it.isNotEmpty() }?.let { binding.editTextDeviceId.setText(it) }
    PRESET_SERIAL_NUMBER.takeIf { it.isNotEmpty() }
      ?.let { binding.editTextSerialNumber.setText(it) }
    PRESET_NAME.takeIf { it.isNotEmpty() }?.let { binding.editTextDeviceName.setText(it) }
  }

  private fun setupRecyclerView() {
    binding.recyclerView.run {
      layoutManager = LinearLayoutManager(this.context)
      adapter = bleResultsAdapter
    }
  }

  private fun setupGenerateCertificateButton() {
    binding.buttonGenerateCertificate.setOnClickListener {
      val deviceName = binding.editTextDeviceName.text.toString()
      val deviceId = binding.editTextDeviceId.text.toString()
      if (deviceId.isNotBlank()) {
        certificateManager.registerAndGenerateCertificate(
          deviceName,
          deviceId.toInt(),
          ::onResetCertificateStatus,
          ::onSuccessfullyGenerateCertificate,
          ::onFailureGenerateCertificate
        )
      } else {
        Toast.makeText(context, "Device ID have to be filled", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun onResetCertificateStatus() {
    changeCertificateStatus("")
  }

  private fun onSuccessfullyGenerateCertificate(
    certificateResponse: MobileCertificateResponse,
    registerResponse: RegisterMobileResponse,
  ) {
    val message = "Generate certificate request successful"
    Timber.d("Register Mobile request successful: $registerResponse")
    changeCertificateStatus(message)
    Timber.d("$message: $certificateResponse")
  }

  private fun onFailureGenerateCertificate(error: Exception) {
    changeCertificateStatus(error.message ?: "Request failed")
    onFailureRequest(error)
  }

  override fun onFailureRequest(error: Throwable) {
    val message = "Error: ${error.message ?: error::class.java.simpleName}"
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    Timber.e(error, message)
  }

  @SuppressLint("SetTextI18n")
  private fun changeCertificateStatus(status: String) {
    binding.certificateStatus.text = "Certificate: $status"
  }

  private fun setupOpenLockParamsSpinner() {
    setupLockParamsSpinner(
      binding.spinnerOpenParams,
      R.array.spinner_open_options
    ) { position ->
      openLockParam = when (position) {
        0 -> BluetoothConstants.PARAM_NONE
        1 -> BluetoothConstants.PARAM_AUTO
        2 -> BluetoothConstants.PARAM_FORCE
        3 -> BluetoothConstants.PARAM_WITHOUT_PULL
        else -> BluetoothConstants.PARAM_NONE
      }
    }
  }

  private fun setupCloseLockParamsSpinner() {
    setupLockParamsSpinner(
      binding.spinnerCloseParams,
      R.array.spinner_close_options
    ) { position ->
      closeLockParam = when (position) {
        0 -> BluetoothConstants.PARAM_NONE
        1 -> BluetoothConstants.PARAM_FORCE
        else -> BluetoothConstants.PARAM_NONE
      }
    }
  }

  private fun setupLockParamsSpinner(
    spinner: Spinner,
    optionsArrayResId: Int,
    onParamSelected: (Int) -> Unit,
  ) {
    ArrayAdapter.createFromResource(
      context,
      optionsArrayResId,
      android.R.layout.simple_spinner_item
    ).also { adapter ->
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      spinner.adapter = adapter
    }

    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        onParamSelected(position)
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
  }
}
