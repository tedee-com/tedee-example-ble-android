import 'package:flutter/services.dart';

/// Service that communicates with native Android code via MethodChannel
/// Handles all Tedee Lock BLE operations through the native Tedee SDK
/// Singleton pattern ensures all screens share the same instance and listeners
class TedeeLockService {
  // Singleton pattern
  static final TedeeLockService _instance = TedeeLockService._internal();
  factory TedeeLockService() => _instance;
  TedeeLockService._internal();

  static const MethodChannel _channel = MethodChannel('com.tedee.flutter/lock');

  /// Connect to lock with certificate
  /// Returns true if connection successful
  Future<bool> connect({
    required String serialNumber,
    required String deviceId,
    required String name,
    bool keepConnection = true,
  }) async {
    try {
      final bool result = await _channel.invokeMethod('connect', {
        'serialNumber': serialNumber,
        'deviceId': deviceId,
        'name': name,
        'keepConnection': keepConnection,
      });
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to connect: ${e.message}');
    }
  }

  /// Disconnect from lock
  Future<void> disconnect() async {
    try {
      await _channel.invokeMethod('disconnect');
    } on PlatformException catch (e) {
      throw Exception('Failed to disconnect: ${e.message}');
    }
  }

  /// Open (unlock) the lock using BLE command 0x51
  Future<String> openLock() async {
    try {
      final String result = await _channel.invokeMethod('openLock');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to open lock: ${e.message}');
    }
  }

  /// Close (lock) the lock using BLE command 0x50
  Future<String> closeLock() async {
    try {
      final String result = await _channel.invokeMethod('closeLock');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to close lock: ${e.message}');
    }
  }

  /// Pull spring using BLE command 0x52
  Future<String> pullSpring() async {
    try {
      final String result = await _channel.invokeMethod('pullSpring');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to pull spring: ${e.message}');
    }
  }

  /// Get lock state
  Future<String> getLockState() async {
    try {
      final String result = await _channel.invokeMethod('getLockState');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to get lock state: ${e.message}');
    }
  }

  /// Get battery level and charging status using command 0x0C
  Future<String> getBattery() async {
    try {
      final String result = await _channel.invokeMethod('getBattery');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to get battery: ${e.message}');
    }
  }

  /// Get firmware version (unsecure connection required)
  Future<String> getFirmwareVersion() async {
    try {
      final String result = await _channel.invokeMethod('getFirmwareVersion');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to get firmware version: ${e.message}');
    }
  }

  /// Get signed time from Tedee API
  Future<String> getSignedTime() async {
    try {
      final String result = await _channel.invokeMethod('getSignedTime');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to get signed time: ${e.message}');
    }
  }

  /// Send custom BLE command (hex format: e.g., "0x51" or "51")
  Future<String> sendCustomCommand(String hexCommand) async {
    try {
      final String result = await _channel.invokeMethod('sendCustomCommand', {
        'hexCommand': hexCommand,
      });
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to send command: ${e.message}');
    }
  }

  /// Download activity logs from lock using GET_LOGS_TLV command (0x2D)
  /// Automatically fetches all available log packages until none remain
  Future<String> getActivityLogs() async {
    try {
      final String result = await _channel.invokeMethod('getActivityLogs');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to get activity logs: ${e.message}');
    }
  }

  /// Start background service for auto-connect
  /// Maintains connection in background and shows persistent notification
  ///
  /// [enableAutoActions] - Enable automatic actions based on lock state:
  ///   - Lock CLOSED → Auto OPEN
  ///   - Lock OPEN → Auto PULL SPRING
  Future<void> startBackgroundService({
    required String serialNumber,
    required String deviceId,
    required String name,
    bool enableAutoActions = false,
  }) async {
    try {
      await _channel.invokeMethod('startBackgroundService', {
        'serialNumber': serialNumber,
        'deviceId': deviceId,
        'name': name,
        'enableAutoActions': enableAutoActions,
      });
    } on PlatformException catch (e) {
      throw Exception('Failed to start background service: ${e.message}');
    }
  }

  /// Stop background service
  Future<void> stopBackgroundService() async {
    try {
      await _channel.invokeMethod('stopBackgroundService');
    } on PlatformException catch (e) {
      throw Exception('Failed to stop background service: ${e.message}');
    }
  }

  /// Check if background service is running
  Future<bool> isBackgroundServiceRunning() async {
    try {
      final bool result = await _channel.invokeMethod('isBackgroundServiceRunning');
      return result;
    } on PlatformException catch (e) {
      return false; // Assume not running if error
    }
  }

  /// Request state sync from background service (only when Flutter is ready)
  Future<bool> requestStateSync() async {
    try {
      final bool result = await _channel.invokeMethod('requestStateSync');
      return result;
    } on PlatformException catch (e) {
      return false;
    }
  }

  // Callback functions - now support multiple listeners
  final List<Function(String)> _notificationListeners = [];
  final List<Function(bool)> _connectionStateListeners = [];
  final List<Function(String)> _lockStateListeners = [];

  bool _methodCallHandlerInitialized = false;

  /// Set up listener for lock notifications from native side
  void setNotificationListener(Function(String) onNotification) {
    if (!_notificationListeners.contains(onNotification)) {
      _notificationListeners.add(onNotification);
    }
    _ensureMethodCallHandler();
  }

  /// Set up listener for connection state changes from background service
  void setConnectionStateListener(Function(bool) onConnectionStateChanged) {
    if (!_connectionStateListeners.contains(onConnectionStateChanged)) {
      _connectionStateListeners.add(onConnectionStateChanged);
    }
    _ensureMethodCallHandler();
  }

  /// Set up listener for lock state changes from background service
  void setLockStateListener(Function(String) onLockStateChanged) {
    if (!_lockStateListeners.contains(onLockStateChanged)) {
      _lockStateListeners.add(onLockStateChanged);
    }
    _ensureMethodCallHandler();
  }

  /// Remove listeners (call from dispose)
  void removeNotificationListener(Function(String) onNotification) {
    _notificationListeners.remove(onNotification);
  }

  void removeConnectionStateListener(Function(bool) onConnectionStateChanged) {
    _connectionStateListeners.remove(onConnectionStateChanged);
  }

  void removeLockStateListener(Function(String) onLockStateChanged) {
    _lockStateListeners.remove(onLockStateChanged);
  }

  /// Internal method to set up method call handler (only once)
  void _ensureMethodCallHandler() {
    if (_methodCallHandlerInitialized) return;

    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onNotification':
          final message = call.arguments as String;
          for (var listener in _notificationListeners) {
            listener(message);
          }
          break;
        case 'onConnectionStateChanged':
          final isConnected = call.arguments as bool;
          for (var listener in _connectionStateListeners) {
            listener(isConnected);
          }
          break;
        case 'onLockStateChanged':
          final lockState = call.arguments as String;
          for (var listener in _lockStateListeners) {
            listener(lockState);
          }
          break;
      }
    });

    _methodCallHandlerInitialized = true;
  }
}
