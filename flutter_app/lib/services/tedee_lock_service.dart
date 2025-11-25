import 'package:flutter/services.dart';

/// Service that communicates with native Android code via MethodChannel
/// Handles all Tedee Lock BLE operations through the native Tedee SDK
class TedeeLockService {
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
  Future<void> startBackgroundService({
    required String serialNumber,
    required String deviceId,
    required String name,
  }) async {
    try {
      await _channel.invokeMethod('startBackgroundService', {
        'serialNumber': serialNumber,
        'deviceId': deviceId,
        'name': name,
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

  /// Set up listener for lock notifications from native side
  void setNotificationListener(Function(String) onNotification) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onNotification') {
        onNotification(call.arguments as String);
      }
    });
  }
}
