package com.tedee.flutter

import android.graphics.Color
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.bluetooth.error.DeviceNeedsResetError
import tedee.mobile.sdk.ble.extentions.getReadableLockCommandResult
import tedee.mobile.sdk.ble.extentions.getReadableLockNotification
import tedee.mobile.sdk.ble.extentions.getReadableLockState
import tedee.mobile.sdk.ble.extentions.getReadableLockStatusResult
import tedee.mobile.sdk.ble.extentions.getReadableStatus
import tedee.mobile.sdk.ble.extentions.print
import com.tedee.flutter.api.service.MobileService
import tedee.mobile.sdk.ble.permissions.getBluetoothPermissions
import com.polidea.rxandroidble2.exceptions.BleException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins

class MainActivity : FlutterActivity(), ILockConnectionListener {
    private val CHANNEL = "com.tedee.flutter/lock"
    private var methodChannel: MethodChannel? = null

    private val lockConnectionManager by lazy { LockConnectionManager(this) }
    private val tedeeFlutterBridge by lazy { TedeeFlutterBridge(this, lockConnectionManager) }
    private val mobileService by lazy { MobileService() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up RxJava error handler for BLE exceptions
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException && throwable.cause is BleException) {
                return@setErrorHandler // ignore BleExceptions since we do not have subscriber
            } else {
                throw throwable
            }
        }

        // Request Bluetooth permissions
        requestPermissions(getBluetoothPermissions().toTypedArray(), 9)

        // Set up SignedTimeProvider for lock connection
        lockConnectionManager.signedDateTimeProvider = SignedTimeProvider(scope)
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize Timber for logging
        if (!Timber.forest().any()) {
            Timber.plant(Timber.DebugTree())
        }

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "connect" -> {
                    val serialNumber = call.argument<String>("serialNumber")
                    val deviceId = call.argument<String>("deviceId")
                    val name = call.argument<String>("name")
                    val keepConnection = call.argument<Boolean>("keepConnection") ?: true

                    if (serialNumber == null || deviceId == null || name == null) {
                        result.error("INVALID_ARGS", "Missing required arguments", null)
                        return@setMethodCallHandler
                    }

                    connectToLock(serialNumber, deviceId, name, keepConnection, result)
                }
                "disconnect" -> {
                    lockConnectionManager.disconnect()
                    result.success(null)
                }
                "openLock" -> {
                    scope.launch {
                        try {
                            // Explicitly provide null for params to avoid DefaultImpls lookup
                            val response = lockConnectionManager.sendCommand(0x51.toByte(), null)
                            val readable = response?.getReadableLockCommandResult() ?: "No response"
                            result.success(readable)
                        } catch (e: Exception) {
                            result.error("OPEN_FAILED", e.message, null)
                        }
                    }
                }
                "closeLock" -> {
                    scope.launch {
                        try {
                            // Explicitly provide null for params to avoid DefaultImpls lookup
                            val response = lockConnectionManager.sendCommand(0x50.toByte(), null)
                            val readable = response?.getReadableLockCommandResult() ?: "No response"
                            result.success(readable)
                        } catch (e: Exception) {
                            result.error("CLOSE_FAILED", e.message, null)
                        }
                    }
                }
                "pullSpring" -> {
                    scope.launch {
                        try {
                            // Explicitly provide null for params to avoid DefaultImpls lookup
                            val response = lockConnectionManager.sendCommand(0x52.toByte(), null)
                            val readable = response?.getReadableLockCommandResult() ?: "No response"
                            result.success(readable)
                        } catch (e: Exception) {
                            result.error("PULL_FAILED", e.message, null)
                        }
                    }
                }
                "getLockState" -> {
                    scope.launch {
                        try {
                            val response = lockConnectionManager.getLockState()
                            // Use getReadableLockStatusResult() for lock state (not getReadableLockCommandResult)
                            val readable = response?.getReadableLockStatusResult() ?: "No response"
                            result.success(readable)
                        } catch (e: Exception) {
                            result.error("GET_STATE_FAILED", e.message, null)
                        }
                    }
                }
                "getBattery" -> {
                    scope.launch {
                        try {
                            // GET_BATTERY command (0x0C)
                            val response = lockConnectionManager.sendCommand(0x0C.toByte(), null)

                            if (response == null || response.size < 4) {
                                result.error("GET_BATTERY_FAILED", "Invalid response", null)
                                return@launch
                            }

                            // Response: [COMMAND_ECHO, RESULT, BATTERY_LEVEL, CHARGING_STATUS]
                            val batteryLevel = response[2].toInt() and 0xFF
                            val chargingStatus = response[3].toInt() and 0xFF
                            val chargingText = if (chargingStatus == 1) "⚡ Charging" else "🔌 Discharging"

                            val batteryInfo = "Battery: $batteryLevel% - $chargingText"
                            result.success(batteryInfo)
                        } catch (e: Exception) {
                            result.error("GET_BATTERY_FAILED", e.message, null)
                        }
                    }
                }
                "getFirmwareVersion" -> {
                    scope.launch {
                        try {
                            // Pass false = lock is already connected (not being added)
                            val response = lockConnectionManager.getFirmwareVersion(false)
                            val readable = response?.toString() ?: "No response"
                            result.success(readable)
                        } catch (e: Exception) {
                            result.error("GET_FIRMWARE_FAILED", e.message, null)
                        }
                    }
                }
                "getSignedTime" -> {
                    scope.launch {
                        try {
                            val signedTime = mobileService.getSignedTime()
                            // SignedTime is an SDK model - use toString() to display it
                            result.success(signedTime.toString())
                        } catch (e: Exception) {
                            result.error("GET_SIGNED_TIME_FAILED", e.message, null)
                        }
                    }
                }
                "sendCustomCommand" -> {
                    // Temporarily disabled due to Kotlin/SDK incompatibility
                    result.error("NOT_IMPLEMENTED", "Custom command feature temporarily disabled", null)
                }
                "getActivityLogs" -> {
                    scope.launch {
                        try {
                            val allLogs = mutableListOf<String>()
                            var packageCount = 0
                            var resultCode: Byte

                            // Loop to download all log packages
                            do {
                                packageCount++
                                val response = lockConnectionManager.sendCommand(0x2D.toByte(), null)

                                if (response == null || response.size < 2) {
                                    result.error("GET_LOGS_FAILED", "Invalid response from lock", null)
                                    return@launch
                                }

                                // Debug: Log the full response
                                val hexBytes = response.joinToString(" ") { byte ->
                                    "0x%02X".format(byte.toInt() and 0xFF)
                                }
                                Timber.d("GET_LOGS response: $hexBytes (size=${response.size})")

                                // Response format: [COMMAND_ECHO, RESULT_CODE, DATA...]
                                // Byte 0: 0x2D (command echo)
                                // Byte 1: Result code
                                val commandEcho = response[0]
                                resultCode = response[1]

                                Timber.d("Command echo: 0x%02X, Result code: 0x%02X".format(
                                    commandEcho.toInt() and 0xFF,
                                    resultCode.toInt() and 0xFF
                                ))

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
                                        result.error("GET_LOGS_FAILED", "MTU too small (minimum 98 bytes required)", null)
                                        return@launch
                                    }
                                    0x07.toByte() -> {
                                        result.error("GET_LOGS_FAILED", "No permission to read logs", null)
                                        return@launch
                                    }
                                    else -> {
                                        val codeHex = "0x%02X".format(resultCode.toInt() and 0xFF)
                                        result.error("GET_LOGS_FAILED", "Unknown result code: $codeHex (full response: $hexBytes)", null)
                                        return@launch
                                    }
                                }

                                // Safety limit to prevent infinite loops
                                if (packageCount >= 100) {
                                    allLogs.add("Warning: Stopped after 100 packages (safety limit)")
                                    break
                                }

                            } while (resultCode != 0x04.toByte()) // Continue until NOT_FOUND

                            val summary = """
                                |📋 Activity Logs Downloaded
                                |
                                |Total packages: $packageCount
                                |${allLogs.joinToString("\n")}
                            """.trimMargin()

                            result.success(summary)
                        } catch (e: Exception) {
                            result.error("GET_LOGS_FAILED", e.message, null)
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun connectToLock(
        serialNumber: String,
        deviceId: String,
        name: String,
        keepConnection: Boolean,
        result: MethodChannel.Result
    ) {
        scope.launch {
            try {
                // Use TedeeFlutterBridge to handle certificate generation and connection
                tedeeFlutterBridge.connect(
                    serialNumber = serialNumber,
                    deviceId = deviceId,
                    name = name,
                    keepConnection = keepConnection,
                    listener = this@MainActivity
                )

                Timber.d("MainActivity: Connection successful")
                result.success(true)
            } catch (e: Exception) {
                Timber.e(e, "MainActivity: Connection failed")
                result.error("CONNECT_FAILED", e.message, null)
            }
        }
    }

    // ILockConnectionListener callbacks
    override fun onLockConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
        Timber.d("Flutter: onLockConnectionChanged - isConnecting: $isConnecting, isConnected: $isConnected")
        val status = when {
            isConnecting -> "Connecting..."
            isConnected -> "✅ Secure session established"
            else -> "Disconnected"
        }
        sendNotificationToFlutter(status)
    }

    override fun onNotification(message: ByteArray) {
        if (message.isEmpty()) return
        Timber.d("Flutter: onNotification: ${message.print()}")

        // Check for HAS_ACTIVITY_LOGS notification (0xA5)
        val firstByte = message.first()
        val notification = when {
            firstByte == 0xA5.toByte() -> {
                "📋 Activity logs available! Lock has stored activity logs ready to download."
            }
            else -> {
                val readableNotification = message.getReadableLockNotification()
                "Notification: $readableNotification"
            }
        }

        sendNotificationToFlutter(notification)
    }

    override fun onLockStatusChanged(currentState: Byte, status: Byte) {
        Timber.d("Flutter: onLockStatusChanged - currentState: $currentState, status: $status")
        val readableState = currentState.getReadableLockState()
        val readableStatus = status.getReadableStatus()
        sendNotificationToFlutter("State: $readableState, Status: $readableStatus")
    }

    override fun onError(throwable: Throwable) {
        Timber.e(throwable, "Flutter: onError")
        when (throwable) {
            is DeviceNeedsResetError -> {
                sendNotificationToFlutter("❌ Device needs factory reset")
            }
            else -> {
                sendNotificationToFlutter("❌ Error: ${throwable.message}")
            }
        }
    }

    private fun sendNotificationToFlutter(message: String) {
        runOnUiThread {
            methodChannel?.invokeMethod("onNotification", message)
        }
    }

    override fun onDestroy() {
        lockConnectionManager.clear()
        super.onDestroy()
    }
}
