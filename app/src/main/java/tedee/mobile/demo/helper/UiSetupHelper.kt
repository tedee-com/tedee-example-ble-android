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
import tedee.mobile.demo.PRESET_DEVICE_ID
import tedee.mobile.demo.PRESET_NAME
import tedee.mobile.demo.PRESET_SERIAL_NUMBER
import tedee.mobile.demo.R
import tedee.mobile.demo.adapter.BleResultItem
import tedee.mobile.demo.adapter.BleResultsAdapter
import tedee.mobile.demo.certificate.data.model.MobileCertificateResponse
import tedee.mobile.demo.certificate.data.model.RegisterMobileResponse
import tedee.mobile.demo.databinding.ActivityMainBinding
import tedee.mobile.demo.manager.CertificateManager
import tedee.mobile.demo.manager.SignedTimeManager
import tedee.mobile.sdk.ble.BluetoothConstants
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.model.SignedTime
import tedee.mobile.sdk.ble.extentions.parseHexStringToByte
import tedee.mobile.sdk.ble.extentions.parseHexStringToByteArray
import tedee.mobile.sdk.ble.model.DeviceCertificate
import timber.log.Timber

class UiSetupHelper(
  private val context: Context,
  private val binding: ActivityMainBinding,
  private val lifecycleScope: LifecycleCoroutineScope,
  private val listener: ILockConnectionListener,
) {

  private val items: MutableList<BleResultItem> = mutableListOf()
  private val bleResultsAdapter: BleResultsAdapter by lazy { BleResultsAdapter() }
  private val certificateManager: CertificateManager by lazy {
    CertificateManager(context, lifecycleScope)
  }
  private val signedTimeManager: SignedTimeManager by lazy { SignedTimeManager(lifecycleScope) }
  private var openLockParam: Byte = BluetoothConstants.PARAM_NONE
  private var closeLockParam: Byte = BluetoothConstants.PARAM_NONE

  fun setup() {
    initPresetValues()
    setupRecyclerView()
    certificateManager.setupMobilePublicKey()
    certificateManager.setupCertificateData()
    setupGenerateCertificateButton()
    setupOpenLockParamsSpinner()
    setupCloseLockParamsSpinner()
  }

  fun setupConnectClickListener(
    connectLock: (
      serialNumber: String,
      deviceCertificate: DeviceCertificate,
      keepConnection: Boolean,
      listener: ILockConnectionListener,
    ) -> Unit,
  ) {
    binding.buttonConnect.setOnClickListener {
      val serialNumber = binding.editTextSerialNumber.text.toString()
      val keepConnection = binding.switchKeepConnection.isChecked
      val deviceCertificate =
        DeviceCertificate(certificateManager.certificate, certificateManager.devicePublicKey)
      connectLock(serialNumber, deviceCertificate, keepConnection, listener)
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

  private fun getAndSetSignedTime(setSignedTime: (SignedTime) -> Unit) {
    signedTimeManager.getSignedTime(setSignedTime, ::onFailureRequest)
  }

  suspend fun getSignedTime() = signedTimeManager.getSignedTime()

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

  fun onFailureRequest(error: Exception) {
    val message = "Request failed"
    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
    Timber.e(error, error.message ?: message)
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
