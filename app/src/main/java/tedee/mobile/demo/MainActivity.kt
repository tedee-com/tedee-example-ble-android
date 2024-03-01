package tedee.mobile.demo

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedee.mobile.demo.adapter.BleResultItem
import tedee.mobile.demo.adapter.BleResultsAdapter
import tedee.mobile.demo.certificate.data.model.MobileRegistrationBody
import tedee.mobile.demo.certificate.service.MobileService
import tedee.mobile.demo.databinding.ActivityMainBinding
import tedee.mobile.demo.datastore.DataStoreManager
import tedee.mobile.demo.datastore.DataStoreManager.getMobilePublicKey
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.extentions.getLockStatus
import tedee.mobile.sdk.ble.extentions.getReadableLockCommandResult
import tedee.mobile.sdk.ble.extentions.getReadableLockNotification
import tedee.mobile.sdk.ble.extentions.getReadableLockState
import tedee.mobile.sdk.ble.extentions.parseHexStringToByte
import tedee.mobile.sdk.ble.extentions.parseHexStringToByteArray
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.keystore.getMobilePublicKey
import tedee.mobile.sdk.ble.model.DeviceCertificate
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import timber.log.Timber

class MainActivity : AppCompatActivity(), ILockConnectionListener {

  private lateinit var binding: ActivityMainBinding
  private val items: MutableList<BleResultItem> = mutableListOf()
  private val bleResultsAdapter: BleResultsAdapter by lazy { BleResultsAdapter() }
  private val lockConnectionManager by lazy { LockConnectionManager(this) }
  private val mobileService by lazy { MobileService() }
  private var shouldKeepConnection = false
  private var certificate: String = ""
  private var devicePublicKey: String = ""
  private var mobilePublicKey: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    Timber.plant(Timber.DebugTree())
    setupMobilePublicKey()
    setupCertificateData()
    setupRecyclerView()
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    setupGenerateCertificateButton()
    binding.buttonConnect.setOnClickListener { connectLock() }
    binding.buttonDisconnect.setOnClickListener { lockConnectionManager.disconnect() }
    binding.switchKeepConnection.setOnCheckedChangeListener { _, isChecked ->
      shouldKeepConnection = isChecked
    }
    binding.buttonSendCommand.setOnClickListener {
      val message = parseHexStringToByte(binding.editTextCommand.text.toString())
      val params = parseHexStringToByteArray(binding.editTextParam.text.toString())
      message?.let { lockConnectionManager.sendCommand(it, params) }
    }
    initPresetValues()
  }

  private fun connectLock() {
    lockConnectionManager.connect(
      serialNumber = binding.editTextSerialNumber.text.toString(),
      deviceCertificate = DeviceCertificate(certificate, devicePublicKey),
      keepConnection = shouldKeepConnection,
      listener = this
    )
  }

  override fun onDestroy() {
    lockConnectionManager.clear()
    super.onDestroy()
  }

  @SuppressLint("SetTextI18n")
  private fun changeConnectingState(state: String, color: Int = Color.WHITE) {
    binding.connectingState.text = "State: $state"
    binding.connectingState.setTextColor(color)
  }

  @SuppressLint("SetTextI18n")
  private fun changeCertificateStatus(status: String) {
    binding.certificateStatus.text = "Certificate: $status"
  }

  override fun onConnectionChanged(isConnecting: Boolean, connected: Boolean) {
    Timber.w("LOCK LISTENER: connection changed: isConnected: $connected")
    binding.clCommands.visibility = View.GONE
    when {
      isConnecting -> {
        changeConnectingState("Connecting", Color.WHITE)
      }

      connected -> {
        changeConnectingState("Secure session established", Color.GREEN)
        binding.clCommands.visibility = View.VISIBLE
      }

      else -> changeConnectingState("Disconnected", Color.RED)
    }
  }

  @SuppressLint("SetTextI18n")
  override fun onIndication(message: ByteArray) {
    Timber.d("LOCK LISTENER message: ${message.print()}")
    val readableResult = message.getReadableLockCommandResult()
    val formattedText = "onIndication: \nResult: $readableResult"
    addMessage(formattedText)
  }

  @SuppressLint("SetTextI18n")
  override fun onNotification(message: ByteArray) {
    if (message.isEmpty()) return
    Timber.d("LOCK LISTENER: notification: ${message.print()}")
    val readableNotification = message.getReadableLockNotification()
    val readableState = if (message.size > 1) message[1].getReadableLockState() else return
    val formattedText = "onNotification: \n$readableNotification " +
        "\nCurrent state: $readableState \nStatus: ${message[2].getLockStatus()}"
    addMessage(formattedText)
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK LISTENER:: error $throwable")
    changeConnectingState("Disconnected", Color.RED)
    val errorMessage = "Error: ${throwable.javaClass.simpleName}"
    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    addMessage(errorMessage)
  }

  private fun setupRecyclerView() {
    binding.recyclerView.run {
      layoutManager = LinearLayoutManager(this.context)
      adapter = bleResultsAdapter
    }
  }

  private fun addMessage(message: String) {
    items.add(0, BleResultItem(message))
    bleResultsAdapter.addItems(items)
    bleResultsAdapter.notifyItemInserted(0)
    binding.recyclerView.scrollToPosition(0)
  }

  private fun setupMobilePublicKey() {
    lifecycleScope.launch {
      mobilePublicKey = getMobilePublicKey(applicationContext) ?: getMobilePublicKey().orEmpty()
    }
  }

  private fun setupCertificateData() {
    lifecycleScope.launch {
      certificate = DataStoreManager.getCertificate(applicationContext)
      devicePublicKey = DataStoreManager.getDevicePublicKey(applicationContext)
    }
  }

  private fun setupGenerateCertificateButton() {
    binding.buttonGenerateCertificate.setOnClickListener {
      val deviceName = binding.editTextDeviceName.text.toString()
      val deviceId = binding.editTextDeviceId.text.toString()
      if (deviceId.isNotBlank()) {
        registerAndGenerateCertificate(deviceName, deviceId.toInt())
      } else {
        Toast.makeText(this, "Device ID have to be filled", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun registerAndGenerateCertificate(deviceName: String, deviceId: Int) {
    changeCertificateStatus("")
    lifecycleScope.launch {
      try {
        val registrationResult = mobileService.registerMobile(
          MobileRegistrationBody(deviceName, publicKey = mobilePublicKey)
        )
        Timber.d("Register Mobile request successful: $registrationResult")
        val certificateResult = mobileService.getCertificate(registrationResult.id, deviceId)
        val message = "Generate certificate request successful"
        changeCertificateStatus(message)
        Timber.d("$message: $certificateResult")
        withContext(Dispatchers.IO) {
          DataStoreManager.saveCertificateData(
            this@MainActivity,
            certificateResult.certificate,
            certificateResult.devicePublicKey,
            certificateResult.mobilePublicKey
          )
        }
        certificate = certificateResult.certificate
        devicePublicKey = certificateResult.devicePublicKey

      } catch (error: Exception) {
        val message = "Request failed"
        Toast.makeText(
          this@MainActivity,
          "Error: ${error.message}",
          Toast.LENGTH_SHORT
        ).show()
        changeCertificateStatus(error.message ?: message)
        Timber.e(error, error.message ?: message)
      }
    }
  }

  private fun initPresetValues() {
    PRESET_DEVICE_ID.takeIf { it.isNotEmpty() }?.let { binding.editTextDeviceId.setText(it) }
    PRESET_SERIAL_NUMBER.takeIf { it.isNotEmpty() }
      ?.let { binding.editTextSerialNumber.setText(it) }
    PRESET_NAME.takeIf { it.isNotEmpty() }?.let { binding.editTextDeviceName.setText(it) }
  }
}