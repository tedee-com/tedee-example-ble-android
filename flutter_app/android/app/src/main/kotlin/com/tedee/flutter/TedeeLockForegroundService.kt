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
        const val ACTION_OPEN_LOCK = "com.tedee.flutter.OPEN_LOCK"
        const val ACTION_CLOSE_LOCK = "com.tedee.flutter.CLOSE_LOCK"
        const val ACTION_PULL_SPRING = "com.tedee.flutter.PULL_SPRING"

        const val EXTRA_SERIAL_NUMBER = "serial_number"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_NAME = "name"
    }

    private lateinit var lockConnectionManager: LockConnectionManager
    private lateinit var certificateManager: CertificateManager
    private lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null

    private var serialNumber: String? = null
    private var deviceId: String? = null
    private var lockName: String? = null
    private var isConnected = false
    private var isConnecting = false
    private var currentLockState: String = "Unknown"

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

                if (serialNumber != null && deviceId != null && lockName != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting service...", false))
                    startAutoConnect()
                } else {
                    Timber.e("Missing lock credentials, stopping service")
                    stopSelf()
                }
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
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
        }

        return START_STICKY
    }

    private fun startAutoConnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            while (true) {
                if (!isConnected && !isConnecting) {
                    try {
                        Timber.d("Auto-connecting to lock...")
                        updateNotification("Connecting to $lockName...", false)
                        connectToLock()
                    } catch (e: Exception) {
                        Timber.e(e, "Auto-connect failed, retrying in 10s")
                        delay(10000) // Retry every 10 seconds
                    }
                } else {
                    delay(5000) // Check connection every 5 seconds
                }
            }
        }
    }

    private suspend fun connectToLock() {
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
            throw e
        }
    }

    private fun executeCommand(commandName: String, command: suspend () -> ByteArray?) {
        if (!isConnected) {
            updateNotification("Not connected - $commandName failed", false)
            return
        }

        serviceScope.launch {
            try {
                updateNotification("Executing $commandName...", true)
                val response = command()
                Timber.d("$commandName result: ${response?.print()}")
                updateNotification("✅ $commandName sent - $currentLockState", true)
            } catch (e: Exception) {
                Timber.e(e, "$commandName failed")
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
                updateNotification("✅ Connected - $currentLockState", true)
            }
            isConnecting -> {
                updateNotification("Connecting to $lockName...", false)
            }
            else -> {
                updateNotification("Disconnected - Reconnecting...", false)
                // Auto-reconnect on disconnect
                serviceScope.launch {
                    delay(3000)
                    if (!isConnected && !isConnecting) {
                        connectToLock()
                    }
                }
            }
        }
    }

    override fun onNotification(message: ByteArray) {
        Timber.d("Service: onNotification - ${message.print()}")

        // Detect HAS_ACTIVITY_LOGS (0xA5)
        if (message.isNotEmpty() && message.first() == 0xA5.toByte()) {
            updateNotification("✅ Connected - Activity logs available", true)
        }
    }

    override fun onLockStatusChanged(currentState: Byte, status: Byte) {
        currentLockState = currentState.getReadableLockState()
        Timber.d("Service: onLockStatusChanged - state=$currentLockState")
        updateNotification("✅ Connected - $currentLockState", true)
    }

    override fun onError(throwable: Throwable) {
        Timber.e(throwable, "Service: onError")
        updateNotification("❌ Error: ${throwable.message}", false)
    }

    override fun onDestroy() {
        Timber.d("TedeeLockForegroundService: onDestroy()")
        reconnectJob?.cancel()
        lockConnectionManager.disconnect()
        lockConnectionManager.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
