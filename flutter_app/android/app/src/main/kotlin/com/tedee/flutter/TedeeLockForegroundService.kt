package com.tedee.flutter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedee.mobile.sdk.ble.bluetooth.ILockConnectionListener
import tedee.mobile.sdk.ble.bluetooth.LockConnectionManager
import tedee.mobile.sdk.ble.extentions.getReadableLockState
import tedee.mobile.sdk.ble.extentions.getReadableLockStatusResult
import tedee.mobile.sdk.ble.extentions.getReadableLockCommandResult
import tedee.mobile.sdk.ble.extentions.print
import tedee.mobile.sdk.ble.model.DeviceCertificate
import timber.log.Timber
import com.tedee.flutter.manager.CertificateManager

/**
 * Foreground service that maintains BLE connection to Tedee lock in background
 * Auto-connects when lock is in range and allows quick lock/unlock from notification
 */
class TedeeLockForegroundService : Service(), ILockConnectionListener {

    companion object {
        private const val CHANNEL_ID = "tedee_lock_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.tedee.flutter.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.tedee.flutter.STOP_SERVICE"
        const val ACTION_SYNC_STATE = "com.tedee.flutter.SYNC_STATE"
        const val ACTION_OPEN_LOCK = "com.tedee.flutter.OPEN_LOCK"
        const val ACTION_CLOSE_LOCK = "com.tedee.flutter.CLOSE_LOCK"
        const val ACTION_PULL_SPRING = "com.tedee.flutter.PULL_SPRING"
        const val ACTION_GET_LOCK_STATE = "com.tedee.flutter.GET_LOCK_STATE"
        const val ACTION_GET_BATTERY = "com.tedee.flutter.GET_BATTERY"
        const val ACTION_GET_FIRMWARE = "com.tedee.flutter.GET_FIRMWARE"

        // Broadcast actions for Flutter communication
        const val BROADCAST_CONNECTION_STATE = "com.tedee.flutter.CONNECTION_STATE"
        const val BROADCAST_LOCK_STATE = "com.tedee.flutter.LOCK_STATE"
        const val BROADCAST_COMMAND_RESULT = "com.tedee.flutter.COMMAND_RESULT"
        const val EXTRA_IS_CONNECTED = "is_connected"
        const val EXTRA_LOCK_STATE = "lock_state"
        const val EXTRA_COMMAND_RESULT = "command_result"

        const val EXTRA_SERIAL_NUMBER = "serial_number"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_ENABLE_AUTO_ACTIONS = "enable_auto_actions"

        // Lock state constants (from Tedee SDK)
        private const val LOCK_STATE_LOCKED: Byte = 0x06
        private const val LOCK_STATE_UNLOCKED: Byte = 0x02

        // Cooldown to prevent rapid-fire actions
        private const val AUTO_ACTION_COOLDOWN_MS = 10000L // 10 seconds
    }

    private lateinit var lockConnectionManager: LockConnectionManager
    private lateinit var certificateManager: CertificateManager
    private lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null
    private var statePollingJob: Job? = null

    private var serialNumber: String? = null
    private var deviceId: String? = null
    private var lockName: String? = null
    private var isConnected = false
    private var isConnecting = false
    private var currentLockState: String = "Unknown"
    private var currentLockStateByte: Byte = 0x00

    // Auto-action settings
    private var autoActionsEnabled = false
    private var lastAutoActionTime = 0L
    private var lastProcessedState: Byte? = null

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.d("TedeeLockForegroundService: onCreate()")

        lockConnectionManager = LockConnectionManager(this)
        certificateManager = CertificateManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("TedeeLockForegroundService: onStartCommand - action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                serialNumber = intent.getStringExtra(EXTRA_SERIAL_NUMBER)
                deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                lockName = intent.getStringExtra(EXTRA_NAME)
                autoActionsEnabled = intent.getBooleanExtra(EXTRA_ENABLE_AUTO_ACTIONS, false)

                if (serialNumber != null && deviceId != null && lockName != null) {
                    val statusMsg = if (autoActionsEnabled) {
                        "Starting service (AUTO-ACTIONS ENABLED)..."
                    } else {
                        "Starting service..."
                    }
                    startForeground(NOTIFICATION_ID, createNotification(statusMsg, false))
                    Timber.i("🚀 AUTO-ACTIONS enabled: $autoActionsEnabled")
                    startAutoConnect()
                } else {
                    Timber.e("Missing lock credentials, stopping service")
                    stopSelf()
                }
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            ACTION_SYNC_STATE -> {
                // Broadcast current state to sync with app UI
                Timber.d("📡 Sync state requested - broadcasting current state")
                broadcastConnectionState(isConnected)
                if (isConnected) {
                    broadcastLockState(currentLockState)
                }
            }
            ACTION_OPEN_LOCK -> {
                executeCommand("Open") {
                    lockConnectionManager.sendCommand(0x51.toByte(), null)
                }
            }
            ACTION_CLOSE_LOCK -> {
                executeCommand("Close") {
                    lockConnectionManager.sendCommand(0x50.toByte(), null)
                }
            }
            ACTION_PULL_SPRING -> {
                executeCommand("Pull Spring") {
                    lockConnectionManager.sendCommand(0x52.toByte(), null)
                }
            }
            ACTION_GET_LOCK_STATE -> {
                executeCommand("Get Lock State") {
                    lockConnectionManager.getLockState()
                }
            }
            ACTION_GET_BATTERY -> {
                executeCommand("Get Battery") {
                    lockConnectionManager.sendCommand(0x0C.toByte(), null)
                }
            }
            ACTION_GET_FIRMWARE -> {
                // getFirmwareVersion returns FirmwareVersion object, not ByteArray, so handle separately
                if (!isConnected) {
                    val errorMsg = "Not connected - Get Firmware failed"
                    updateNotification(errorMsg, false)
                    broadcastCommandResult(errorMsg)
                } else {
                    serviceScope.launch {
                        try {
                            updateNotification("Getting firmware version...", true)
                            val firmwareVersion = lockConnectionManager.getFirmwareVersion(false)
                            val versionString = firmwareVersion?.toString() ?: "No response"
                            Timber.d("Firmware version: $versionString")
                            broadcastCommandResult(versionString)
                            updateNotification("✅ Firmware: $versionString - $currentLockState", true)
                        } catch (e: Exception) {
                            Timber.e(e, "Get firmware failed")
                            val errorMsg = "❌ Get Firmware failed: ${e.message}"
                            broadcastCommandResult(errorMsg)
                            updateNotification("❌ Get Firmware failed - $currentLockState", true)
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startAutoConnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            // CRITICAL FIX: Add initial delay to allow app UI to load first
            // This prevents the service from blocking the splash screen
            Timber.d("Service started - waiting 3 seconds before first connection attempt")
            delay(3000L) // Wait 3 seconds for app UI to fully load

            var retryDelay = 5000L // Start with 5 seconds
            val maxRetryDelay = 60000L // Max 60 seconds

            while (true) {
                if (!isConnected && !isConnecting) {
                    try {
                        Timber.d("Auto-connecting to lock...")
                        updateNotification("Connecting to $lockName...", false)

                        // CRITICAL FIX: Launch connection in separate coroutine to avoid blocking
                        serviceScope.launch {
                            try {
                                connectToLock()
                            } catch (e: Exception) {
                                Timber.e(e, "Connection attempt failed")
                            }
                        }

                        // Reset retry delay on successful connection attempt
                        retryDelay = 5000L
                    } catch (e: ScanThrottleException) {
                        // BLE scan throttle - use longer delay
                        val throttleDelay = 60000L // Wait 60 seconds for throttle
                        Timber.w("BLE scan throttled, waiting ${throttleDelay/1000}s before retry")
                        updateNotification("⚠️ BLE throttled - waiting ${throttleDelay/1000}s", false)
                        delay(throttleDelay)
                    } catch (e: Exception) {
                        Timber.e(e, "Auto-connect failed, retrying in ${retryDelay/1000}s")
                        updateNotification("Connection failed - retry in ${retryDelay/1000}s", false)
                        delay(retryDelay)

                        // Exponential backoff (double delay, up to max)
                        retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                    }
                } else {
                    // Check connection status every 5 seconds when connected/connecting
                    delay(5000)
                }
            }
        }
    }

    /**
     * Start periodic polling of lock state
     * Cylinders don't send automatic state notifications, so we poll manually
     */
    private fun startStatePolling() {
        statePollingJob?.cancel()
        statePollingJob = serviceScope.launch {
            // FIRST CHECK: Read state IMMEDIATELY after secure connection
            try {
                Timber.i("🔍 Initial state check after secure connection...")
                val response = lockConnectionManager.getLockState()

                if (response != null && response.size >= 2) {
                    val state = response[1]
                    val stateHex = "0x%02X".format(state.toInt() and 0xFF)
                    Timber.i("📊 Initial state: ${state.getReadableLockState()} ($stateHex)")

                    // Trigger auto-actions immediately if needed
                    handleLockStateUpdate(state)
                }
            } catch (e: Exception) {
                Timber.e(e, "Initial state check failed")
            }

            // CONTINUOUS POLLING: Then poll every 5 seconds
            while (isConnected) {
                try {
                    delay(5000) // Wait 5 seconds between checks

                    if (isConnected) {
                        Timber.d("🔍 Polling lock state...")
                        val response = lockConnectionManager.getLockState()

                        if (response != null && response.size >= 2) {
                            val state = response[1]
                            val stateHex = "0x%02X".format(state.toInt() and 0xFF)
                            Timber.i("📊 Polled state: ${state.getReadableLockState()} ($stateHex)")

                            handleLockStateUpdate(state)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "State polling failed")
                }
            }
        }
    }

    /**
     * Handle lock state update (called from both callback and polling)
     */
    private fun handleLockStateUpdate(state: Byte) {
        currentLockStateByte = state
        currentLockState = state.getReadableLockState()
        val stateHex = "0x%02X".format(state.toInt() and 0xFF)

        Timber.i("🔔 State updated: $currentLockState ($stateHex)")

        // Broadcast lock state to Flutter
        broadcastLockState(currentLockState)

        // Check if we should perform auto-action
        Timber.d("Auto-actions check: enabled=$autoActionsEnabled, connected=$isConnected")
        if (autoActionsEnabled && isConnected) {
            Timber.i("🎯 Checking auto-action for state: $currentLockState ($stateHex)")
            performAutoActionIfNeeded(state)
        } else {
            if (!autoActionsEnabled) {
                Timber.d("Auto-actions disabled, skipping")
            }
            if (!isConnected) {
                Timber.d("Not connected, skipping auto-actions")
            }
        }

        updateNotification("✅ Connected - $currentLockState", true)
    }

    private suspend fun connectToLock() {
        // CRITICAL: Set isConnecting BEFORE calling connect to prevent loop
        isConnecting = true

        try {
            val cert = certificateManager.registerAndGenerateCertificate(
                serialNumber = serialNumber!!,
                deviceId = deviceId!!,
                name = lockName!!
            )

            lockConnectionManager.signedDateTimeProvider = SignedTimeProvider(serviceScope)
            lockConnectionManager.connect(
                serialNumber = serialNumber!!,
                deviceCertificate = cert,
                keepConnection = true,
                secureConnectionListener = this
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect")
            isConnecting = false  // Reset on failure

            // Check for BLE scan throttle error
            val errorMsg = e.message ?: e.toString()
            if (errorMsg.contains("2147483646") ||
                (errorMsg.contains("scan", ignoreCase = true) &&
                 errorMsg.contains("throttle", ignoreCase = true))) {
                Timber.w("BLE scan throttle detected - waiting longer before retry")
                throw ScanThrottleException("BLE scan throttled by Android", e)
            }

            throw e
        }
    }

    // Custom exception for scan throttle
    class ScanThrottleException(message: String, cause: Throwable?) : Exception(message, cause)

    private fun executeCommand(commandName: String, command: suspend () -> ByteArray?) {
        if (!isConnected) {
            val errorMsg = "Not connected - $commandName failed"
            updateNotification(errorMsg, false)
            broadcastCommandResult(errorMsg)
            return
        }

        serviceScope.launch {
            try {
                updateNotification("Executing $commandName...", true)
                val response = command()
                Timber.d("$commandName result: ${response?.print()}")

                // Format result based on command type
                val readable = when (commandName) {
                    "Get Lock State" -> response?.getReadableLockStatusResult() ?: "No response"
                    "Get Battery" -> {
                        if (response != null && response.size >= 4) {
                            val batteryLevel = response[2].toInt() and 0xFF
                            val chargingStatus = response[3].toInt() and 0xFF
                            val chargingText = if (chargingStatus == 1) "⚡ Charging" else "🔌 Discharging"
                            "Battery: $batteryLevel% - $chargingText"
                        } else {
                            "Invalid battery response"
                        }
                    }
                    else -> response?.getReadableLockCommandResult() ?: "No response"
                }

                broadcastCommandResult(readable)
                updateNotification("✅ $commandName sent - $currentLockState", true)
            } catch (e: Exception) {
                Timber.e(e, "$commandName failed")
                val errorMsg = "❌ $commandName failed: ${e.message}"
                broadcastCommandResult(errorMsg)
                updateNotification("❌ $commandName failed - $currentLockState", true)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tedee Lock Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains connection to Tedee lock in background"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(status: String, showActions: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔐 Tedee Lock: ${lockName ?: "Unknown"}")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Add quick action buttons when connected
        if (showActions && isConnected) {
            val openIntent = createActionIntent(ACTION_OPEN_LOCK)
            builder.addAction(
                android.R.drawable.ic_lock_lock,
                "Open",
                openIntent
            )

            val closeIntent = createActionIntent(ACTION_CLOSE_LOCK)
            builder.addAction(
                android.R.drawable.ic_lock_lock,
                "Close",
                closeIntent
            )

            val pullIntent = createActionIntent(ACTION_PULL_SPRING)
            builder.addAction(
                android.R.drawable.ic_menu_edit,
                "Pull",
                pullIntent
            )
        }

        val stopIntent = createActionIntent(ACTION_STOP_SERVICE)
        builder.addAction(
            android.R.drawable.ic_delete,
            "Stop Service",
            stopIntent
        )

        return builder.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, TedeeLockForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification(status: String, showActions: Boolean) {
        val notification = createNotification(status, showActions)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // ILockConnectionListener callbacks
    override fun onLockConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
        Timber.d("Service: onLockConnectionChanged - isConnecting=$isConnecting, isConnected=$isConnected")
        this.isConnecting = isConnecting
        this.isConnected = isConnected

        when {
            isConnected -> {
                val autoMsg = if (autoActionsEnabled) " (AUTO-ACTIONS ON)" else ""
                updateNotification("✅ Connected$autoMsg - $currentLockState", true)
                // Reset processed state on new connection so auto-actions can trigger
                lastProcessedState = null

                // Broadcast connection state to Flutter
                broadcastConnectionState(true)

                // Start polling lock state (cylinders don't send automatic notifications)
                Timber.i("🔄 Starting state polling for cylinder...")
                startStatePolling()
            }
            isConnecting -> {
                updateNotification("Connecting to $lockName...", false)
            }
            else -> {
                updateNotification("Disconnected - Auto-reconnect active", false)
                // Don't manually reconnect here - let the auto-connect loop handle it
                // This prevents multiple concurrent connection attempts
                // Reset processed state on disconnect
                lastProcessedState = null

                // Broadcast disconnection to Flutter
                broadcastConnectionState(false)

                // Stop polling when disconnected
                statePollingJob?.cancel()
                statePollingJob = null
            }
        }
    }

    /**
     * Broadcast connection state to MainActivity for Flutter
     */
    private fun broadcastConnectionState(connected: Boolean) {
        val intent = Intent(BROADCAST_CONNECTION_STATE).apply {
            putExtra(EXTRA_IS_CONNECTED, connected)
        }
        sendBroadcast(intent)
        Timber.d("📡 Broadcast connection state: $connected")
    }

    /**
     * Broadcast lock state to MainActivity for Flutter
     */
    private fun broadcastLockState(state: String) {
        val intent = Intent(BROADCAST_LOCK_STATE).apply {
            putExtra(EXTRA_LOCK_STATE, state)
        }
        sendBroadcast(intent)
        Timber.d("📡 Broadcast lock state: $state")
    }

    private fun broadcastCommandResult(result: String) {
        val intent = Intent(BROADCAST_COMMAND_RESULT).apply {
            putExtra(EXTRA_COMMAND_RESULT, result)
        }
        sendBroadcast(intent)
        Timber.d("📡 Broadcast command result: $result")
    }

    override fun onNotification(message: ByteArray) {
        Timber.d("Service: onNotification - ${message.print()}")

        // Detect HAS_ACTIVITY_LOGS (0xA5)
        if (message.isNotEmpty() && message.first() == 0xA5.toByte()) {
            updateNotification("✅ Connected - Activity logs available", true)
        }
    }

    override fun onLockStatusChanged(currentState: Byte, status: Byte) {
        val stateHex = "0x%02X".format(currentState.toInt() and 0xFF)
        Timber.i("🔔 onLockStatusChanged callback - state=${currentState.getReadableLockState()} ($stateHex), status=$status")

        // Use centralized state update handler
        handleLockStateUpdate(currentState)
    }

    private fun performAutoActionIfNeeded(lockState: Byte) {
        val stateHex = "0x%02X".format(lockState.toInt() and 0xFF)
        Timber.i("🤖 performAutoActionIfNeeded called with state: $stateHex")

        // Check cooldown period
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAction = currentTime - lastAutoActionTime

        if (timeSinceLastAction < AUTO_ACTION_COOLDOWN_MS) {
            val remainingSeconds = (AUTO_ACTION_COOLDOWN_MS - timeSinceLastAction) / 1000
            Timber.w("⏳ Auto-action cooldown active ($remainingSeconds s remaining)")
            return
        }

        // Check if this state was already processed
        val lastStateHex = lastProcessedState?.let { "0x%02X".format(it.toInt() and 0xFF) } ?: "null"
        if (lastProcessedState == lockState) {
            Timber.w("♻️ Lock state $stateHex already processed (last=$lastStateHex), skipping")
            return
        }

        Timber.i("✅ Auto-action checks passed, state=$stateHex (LOCKED=0x06, UNLOCKED=0x02)")

        // Perform auto-action based on lock state
        when (lockState) {
            LOCK_STATE_LOCKED -> {
                // Lock is CLOSED → AUTO OPEN
                Timber.i("🔓 AUTO-ACTION: Lock is CLOSED, opening automatically...")
                updateNotification("🔓 AUTO: Opening lock...", true)

                serviceScope.launch {
                    try {
                        val result = lockConnectionManager.sendCommand(0x51.toByte(), null)
                        Timber.d("Auto-open result: ${result?.print()}")
                        updateNotification("✅ AUTO: Lock opened", true)
                        lastAutoActionTime = currentTime
                        lastProcessedState = lockState
                    } catch (e: Exception) {
                        Timber.e(e, "Auto-open failed")
                        updateNotification("❌ AUTO: Open failed - ${e.message}", true)
                    }
                }
            }
            LOCK_STATE_UNLOCKED -> {
                // Lock is OPEN → AUTO PULL SPRING + CLOSE
                Timber.i("🔃 AUTO-ACTION: Lock is OPEN, executing PULL SPRING + CLOSE sequence...")
                updateNotification("🔃 AUTO: Pull spring + Close...", true)

                serviceScope.launch {
                    try {
                        // Step 1: Pull spring
                        Timber.d("Step 1/2: Pulling spring...")
                        val pullResult = lockConnectionManager.sendCommand(0x52.toByte(), null)
                        Timber.d("Pull spring result: ${pullResult?.print()}")

                        // Wait for pull spring to complete
                        delay(2000) // 2 seconds delay between commands

                        // Step 2: Close lock
                        Timber.d("Step 2/2: Closing lock...")
                        val closeResult = lockConnectionManager.sendCommand(0x50.toByte(), null)
                        Timber.d("Close lock result: ${closeResult?.print()}")

                        updateNotification("✅ AUTO: Pull + Close completed", true)
                        lastAutoActionTime = currentTime
                        lastProcessedState = lockState
                    } catch (e: Exception) {
                        Timber.e(e, "Auto pull+close failed")
                        updateNotification("❌ AUTO: Pull+Close failed - ${e.message}", true)
                    }
                }
            }
            else -> {
                val stateHex = "0x%02X".format(lockState.toInt() and 0xFF)
                Timber.i("❓ Lock state $stateHex (${lockState.getReadableLockState()}) - no auto-action defined")
            }
        }
    }

    override fun onError(throwable: Throwable) {
        Timber.e(throwable, "Service: onError")

        // Check for BLE scan throttle error
        val errorMsg = throwable.message ?: throwable.toString()
        if (errorMsg.contains("2147483646") ||
            (errorMsg.contains("scan", ignoreCase = true) &&
             errorMsg.contains("throttle", ignoreCase = true))) {
            Timber.w("BLE scan throttle detected in onError")
            updateNotification("⚠️ BLE scan throttled - slowing reconnect", false)
            // Reset connection flags so auto-connect can retry with throttle handling
            isConnecting = false
            isConnected = false
        } else {
            updateNotification("❌ Error: ${throwable.message}", false)
        }
    }

    override fun onDestroy() {
        Timber.d("TedeeLockForegroundService: onDestroy()")
        reconnectJob?.cancel()
        statePollingJob?.cancel()
        lockConnectionManager.disconnect()
        lockConnectionManager.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
