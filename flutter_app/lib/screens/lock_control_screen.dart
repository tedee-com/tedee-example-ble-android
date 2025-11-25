import 'package:flutter/material.dart';
import '../services/tedee_lock_service.dart';

class LockControlScreen extends StatefulWidget {
  const LockControlScreen({super.key});

  @override
  State<LockControlScreen> createState() => _LockControlScreenState();
}

class _LockControlScreenState extends State<LockControlScreen> {
  final TedeeLockService _lockService = TedeeLockService();

  bool _isConnected = false;
  bool _isConnecting = false;
  bool _keepConnection = true;
  bool _isBackgroundServiceRunning = false;
  bool _autoActionsEnabled = false;
  final List<String> _messages = [];

  // Editable fields with preset values from Constants.kt
  final TextEditingController _serialNumberController =
      TextEditingController(text: '10530206-030484');
  final TextEditingController _deviceIdController =
      TextEditingController(text: '273450');
  final TextEditingController _nameController =
      TextEditingController(text: 'Lock-40C5');
  final TextEditingController _customCommandController =
      TextEditingController();

  @override
  void initState() {
    super.initState();
    _lockService.setNotificationListener((message) {
      setState(() {
        _messages.insert(0, message);
      });
    });
  }

  @override
  void dispose() {
    _serialNumberController.dispose();
    _deviceIdController.dispose();
    _nameController.dispose();
    _customCommandController.dispose();
    super.dispose();
  }

  Future<void> _connect() async {
    setState(() {
      _isConnecting = true;
    });

    try {
      final success = await _lockService.connect(
        serialNumber: _serialNumberController.text,
        deviceId: _deviceIdController.text,
        name: _nameController.text,
        keepConnection: _keepConnection,
      );

      setState(() {
        _isConnected = success;
        _isConnecting = false;
        if (success) {
          _messages.insert(0, '✅ Connected to lock');
        }
      });
    } catch (e) {
      setState(() {
        _isConnecting = false;
        _messages.insert(0, '❌ Connection failed: $e');
      });
    }
  }

  Future<void> _disconnect() async {
    try {
      await _lockService.disconnect();
      setState(() {
        _isConnected = false;
        _messages.insert(0, '🔌 Disconnected from lock');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Disconnect failed: $e');
      });
    }
  }

  Future<void> _openLock() async {
    try {
      final result = await _lockService.openLock();
      setState(() {
        _messages.insert(0, '🔓 Open: $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Open failed: $e');
      });
    }
  }

  Future<void> _closeLock() async {
    try {
      final result = await _lockService.closeLock();
      setState(() {
        _messages.insert(0, '🔒 Close: $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Close failed: $e');
      });
    }
  }

  Future<void> _pullSpring() async {
    try {
      final result = await _lockService.pullSpring();
      setState(() {
        _messages.insert(0, '🔧 Pull Spring: $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Pull spring failed: $e');
      });
    }
  }

  Future<void> _getLockState() async {
    try {
      final result = await _lockService.getLockState();
      setState(() {
        _messages.insert(0, '📊 Lock State: $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Get state failed: $e');
      });
    }
  }

  Future<void> _getBattery() async {
    try {
      final result = await _lockService.getBattery();
      setState(() {
        _messages.insert(0, '🔋 $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Get battery failed: $e');
      });
    }
  }

  Future<void> _getFirmwareVersion() async {
    try {
      final result = await _lockService.getFirmwareVersion();
      setState(() {
        _messages.insert(0, '📱 Firmware Version: $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Get firmware failed: $e');
      });
    }
  }

  Future<void> _getSignedTime() async {
    try {
      final result = await _lockService.getSignedTime();
      setState(() {
        _messages.insert(0, '🕐 Signed Time: $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Get signed time failed: $e');
      });
    }
  }

  Future<void> _getActivityLogs() async {
    setState(() {
      _messages.insert(0, '📋 Downloading activity logs...');
    });

    try {
      final result = await _lockService.getActivityLogs();
      setState(() {
        _messages.insert(0, result);
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Failed to download logs: $e');
      });
    }
  }

  Future<void> _startBackgroundService() async {
    try {
      await _lockService.startBackgroundService(
        serialNumber: _serialNumberController.text,
        deviceId: _deviceIdController.text,
        name: _nameController.text,
        enableAutoActions: _autoActionsEnabled,
      );
      setState(() {
        _isBackgroundServiceRunning = true;
        final autoMsg = _autoActionsEnabled ? ' with AUTO-ACTIONS 🚀' : '';
        _messages.insert(0, '🔄 Background service started$autoMsg');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Failed to start background service: $e');
      });
    }
  }

  Future<void> _stopBackgroundService() async {
    try {
      await _lockService.stopBackgroundService();
      setState(() {
        _isBackgroundServiceRunning = false;
        _messages.insert(0, '⏹️ Background service stopped');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Failed to stop background service: $e');
      });
    }
  }

  Future<void> _sendCustomCommand() async {
    final command = _customCommandController.text.trim();
    if (command.isEmpty) {
      setState(() {
        _messages.insert(0, '❌ Please enter a command');
      });
      return;
    }

    try {
      final result = await _lockService.sendCustomCommand(command);
      setState(() {
        _messages.insert(0, '📤 Custom Command ($command): $result');
      });
    } catch (e) {
      setState(() {
        _messages.insert(0, '❌ Send command failed: $e');
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Tedee Lock Control'),
        backgroundColor: const Color(0xFF22345a), // midnight_blue
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Configuration Section
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Lock Configuration',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 16),
                            TextField(
                              controller: _serialNumberController,
                              decoration: const InputDecoration(
                                labelText: 'Serial Number',
                                border: OutlineInputBorder(),
                                prefixIcon: Icon(Icons.tag),
                              ),
                              enabled: !_isConnected,
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              controller: _deviceIdController,
                              decoration: const InputDecoration(
                                labelText: 'Device ID',
                                border: OutlineInputBorder(),
                                prefixIcon: Icon(Icons.fingerprint),
                              ),
                              keyboardType: TextInputType.number,
                              enabled: !_isConnected,
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              controller: _nameController,
                              decoration: const InputDecoration(
                                labelText: 'Lock Name',
                                border: OutlineInputBorder(),
                                prefixIcon: Icon(Icons.label),
                              ),
                              enabled: !_isConnected,
                            ),
                            const SizedBox(height: 16),
                            SwitchListTile(
                              title: const Text('Keep Connection'),
                              subtitle: const Text(
                                'Maintain indefinite connection to lock',
                              ),
                              value: _keepConnection,
                              onChanged: _isConnected
                                  ? null
                                  : (value) {
                                      setState(() {
                                        _keepConnection = value;
                                      });
                                    },
                              activeColor: const Color(0xFF22345a),
                            ),
                            const Divider(),
                            SwitchListTile(
                              title: const Text('Background Auto-Connect'),
                              subtitle: Text(
                                _isBackgroundServiceRunning
                                    ? '🔄 Service running - Auto-connects when nearby'
                                    : 'Start background service for auto-connect',
                              ),
                              value: _isBackgroundServiceRunning,
                              onChanged: (value) async {
                                if (value) {
                                  await _startBackgroundService();
                                } else {
                                  await _stopBackgroundService();
                                }
                              },
                              activeColor: Colors.green,
                            ),
                            Padding(
                              padding: const EdgeInsets.only(left: 16.0),
                              child: SwitchListTile(
                                title: const Text('🚀 Auto-Actions (Proximity)'),
                                subtitle: Text(
                                  _autoActionsEnabled
                                      ? '🔓 Lock CLOSED → Auto OPEN\n🔃 Lock OPEN → Auto PULL SPRING${_isBackgroundServiceRunning ? '\n⚠️ Changing will restart service' : ''}'
                                      : 'Enable automatic lock/unlock when nearby${_isBackgroundServiceRunning ? '\n⚠️ Changing will restart service' : '\n(Requires background service)'}',
                                  style: const TextStyle(fontSize: 12),
                                ),
                                value: _autoActionsEnabled,
                                onChanged: (value) async {
                                  setState(() {
                                    _autoActionsEnabled = value;
                                  });

                                  // If background service is running, restart it with new config
                                  if (_isBackgroundServiceRunning) {
                                    await _stopBackgroundService();
                                    // Small delay to ensure clean shutdown
                                    await Future.delayed(const Duration(milliseconds: 500));
                                    await _startBackgroundService();
                                  }
                                },
                                activeColor: Colors.orange,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),

                    const SizedBox(height: 16),

                    // Connection Status & Controls
                    Card(
                      color: _isConnected ? Colors.green[50] : Colors.grey[100],
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          children: [
                            Text(
                              _isConnecting
                                  ? 'Connecting...'
                                  : _isConnected
                                      ? '✅ Connected'
                                      : '⚫ Disconnected',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                                color: _isConnected ? Colors.green : Colors.grey,
                              ),
                            ),
                            const SizedBox(height: 16),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                ElevatedButton.icon(
                                  onPressed: _isConnecting || _isConnected
                                      ? null
                                      : _connect,
                                  icon: const Icon(Icons.bluetooth_connected),
                                  label: const Text('Connect'),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.green,
                                      foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 24,
                                      vertical: 12,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 16),
                                ElevatedButton.icon(
                                  onPressed: _isConnected ? _disconnect : null,
                                  icon: const Icon(Icons.bluetooth_disabled),
                                  label: const Text('Disconnect'),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.red,
                                      foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 24,
                                      vertical: 12,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),

                    const SizedBox(height: 16),

                    // Lock Control Commands
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Lock Commands',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Expanded(
                                  child: ElevatedButton.icon(
                                    onPressed: _isConnected ? _openLock : null,
                                    icon: const Icon(Icons.lock_open),
                                    label: const Text('Open'),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.green,
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.all(16),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: ElevatedButton.icon(
                                    onPressed: _isConnected ? _closeLock : null,
                                    icon: const Icon(Icons.lock),
                                    label: const Text('Close'),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.red,
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.all(16),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                Expanded(
                                  child: ElevatedButton.icon(
                                    onPressed: _isConnected ? _pullSpring : null,
                                    icon: const Icon(
                                      Icons.settings_input_component,
                                    ),
                                    label: const Text('Pull Spring'),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.orange,
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.all(16),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: ElevatedButton.icon(
                                    onPressed: _isConnected ? _getLockState : null,
                                    icon: const Icon(Icons.info),
                                    label: const Text('Get State'),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.blue,
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.all(16),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),

                    const SizedBox(height: 16),

                    // Device Information Commands
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Device Information',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Expanded(
                                  child: ElevatedButton.icon(
                                    onPressed: _isConnected
                                        ? _getBattery
                                        : null,
                                    icon: const Icon(Icons.battery_std),
                                    label: const Text('Get Battery'),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.purple,
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.all(16),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: ElevatedButton.icon(
                                    onPressed: _isConnected
                                        ? _getFirmwareVersion
                                        : null,
                                    icon: const Icon(Icons.system_update),
                                    label: const Text('Firmware'),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.teal,
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.all(16),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            SizedBox(
                              width: double.infinity,
                              child: ElevatedButton.icon(
                                onPressed: _getSignedTime,
                                icon: const Icon(Icons.access_time),
                                label: const Text('Get Signed Time'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.indigo,
                                      foregroundColor: Colors.white,
                                  padding: const EdgeInsets.all(16),
                                ),
                              ),
                            ),
                            const SizedBox(height: 8),
                            SizedBox(
                              width: double.infinity,
                              child: ElevatedButton.icon(
                                onPressed: _getActivityLogs,
                                icon: const Icon(Icons.history),
                                label: const Text('Download Activity Logs'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.deepPurple,
                                      foregroundColor: Colors.white,
                                  padding: const EdgeInsets.all(16),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),

                    // const SizedBox(height: 16),

                    // Custom Command Section - TEMPORARILY DISABLED
                    // TODO: Re-enable when Kotlin/SDK compatibility issue is resolved
                    /* Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Send Custom Command',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Expanded(
                                  child: TextField(
                                    controller: _customCommandController,
                                    decoration: const InputDecoration(
                                      labelText: 'Hex Command',
                                      hintText: 'e.g., 0x51 or 51',
                                      border: OutlineInputBorder(),
                                      prefixIcon: Icon(Icons.code),
                                    ),
                                    enabled: _isConnected,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                ElevatedButton.icon(
                                  onPressed: _isConnected
                                      ? _sendCustomCommand
                                      : null,
                                  icon: const Icon(Icons.send),
                                  label: const Text('Send'),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.deepOrange,
                                      foregroundColor: Colors.white,
                                    padding: const EdgeInsets.all(16),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'Common commands: 0x50 (Lock), 0x51 (Unlock), 0x52 (Pull Spring)',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ), */
                  ],
                ),
              ),
            ),
          ),

          // Messages Log
          Container(
            height: 200,
            color: Colors.black87,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  color: Colors.grey[900],
                  child: const Row(
                    children: [
                      Icon(Icons.message, color: Colors.white, size: 16),
                      SizedBox(width: 8),
                      Text(
                        'Messages Log',
                        style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: _messages.isEmpty
                      ? const Center(
                          child: Text(
                            'No messages yet',
                            style: TextStyle(color: Colors.grey),
                          ),
                        )
                      : ListView.builder(
                          itemCount: _messages.length,
                          itemBuilder: (context, index) {
                            return Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 8,
                              ),
                              decoration: BoxDecoration(
                                border: Border(
                                  bottom: BorderSide(color: Colors.grey[800]!),
                                ),
                              ),
                              child: Text(
                                _messages[index],
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontFamily: 'monospace',
                                  fontSize: 12,
                                ),
                              ),
                            );
                          },
                        ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
