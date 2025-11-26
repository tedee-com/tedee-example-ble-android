import 'dart:async';
import 'package:flutter/material.dart';
import '../services/tedee_lock_service.dart';

class SimpleLockScreen extends StatefulWidget {
  const SimpleLockScreen({super.key});

  @override
  State<SimpleLockScreen> createState() => _SimpleLockScreenState();
}

class _SimpleLockScreenState extends State<SimpleLockScreen> with TickerProviderStateMixin {
  final TedeeLockService _lockService = TedeeLockService();

  bool _isConnected = false;
  bool _isConnecting = false;
  bool _keepConnection = true;
  bool _autoModeStarted = false;
  bool _smartActionsEnabled = false; // Smart actions: auto open/close based on state
  String _lockState = "Unknown";
  final List<String> _logs = [];

  // Editable fields with preset values
  final TextEditingController _serialNumberController =
      TextEditingController(text: '10530206-030484');
  final TextEditingController _deviceIdController =
      TextEditingController(text: '273450');
  final TextEditingController _nameController =
      TextEditingController(text: 'Lock-40C5');

  // Animation controller for smooth circle movement
  late AnimationController _animationController;
  late Animation<double> _circlePositionAnimation;

  // Animation controller for operation indicator (spinning dot)
  late AnimationController _operationAnimationController;
  late Animation<double> _operationRotationAnimation;

  // Circle position: 0.0 = left, 0.5 = center, 1.0 = right
  double _targetCirclePosition = 0.5;

  // Drag tracking
  double _dragStartX = 0.0;
  double _currentDragPosition = 0.5;
  bool _isDragging = false;

  // Rotation direction: true = clockwise (unlock), false = counterclockwise (lock, pull spring)
  bool _rotateClockwise = true;

  // Timer for debouncing state updates to avoid visual glitches
  Timer? _stateUpdateDebounceTimer;

  // Store listener references for cleanup
  late Function(bool) _connectionListener;
  late Function(String) _stateListener;
  late Function(String) _notificationListener;

  void _addLog(String message) {
    setState(() {
      _logs.insert(0, message);
      if (_logs.length > 50) _logs.removeLast(); // Keep last 50 logs
    });
  }

  // Check if lock is performing an operation (to show spinning indicator)
  bool _isLockOperating() {
    final stateLower = _lockState.toLowerCase();
    return stateLower.contains('locking') ||
           stateLower.contains('unlocking') ||
           stateLower.contains('pulling');
  }

  @override
  void initState() {
    super.initState();

    // Initialize animation controller for circle movement
    _animationController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );

    _circlePositionAnimation = Tween<double>(begin: 0.5, end: 0.5).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeInOut),
    )..addListener(() {
      if (!_isDragging) {
        setState(() {});
      }
    });

    // Initialize animation controller for operation indicator (spinning dot)
    _operationAnimationController = AnimationController(
      duration: const Duration(milliseconds: 1500), // Slow, smooth rotation
      vsync: this,
    );

    _operationRotationAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _operationAnimationController, curve: Curves.linear),
    )..addListener(() {
      setState(() {});
    });

    // Listen for connection state changes
    _connectionListener = (isConnected) {
      _addLog('🔌 Connection: ${isConnected ? "CONNECTED" : "DISCONNECTED"}');
      setState(() {
        _isConnected = isConnected;
        if (!isConnected) {
          _lockState = "Unknown";
          _updateCirclePosition(0.5); // Center when disconnected
        }
      });
    };
    _lockService.setConnectionStateListener(_connectionListener);

    // Listen for lock state changes
    _stateListener = (lockState) {
      _addLog('🔔 Lock State: "$lockState" (toLowerCase: "${lockState.toLowerCase()}")');
      setState(() {
        _lockState = lockState;
        _updateCirclePositionBasedOnState();
      });
    };
    _lockService.setLockStateListener(_stateListener);

    // Listen for notifications
    _notificationListener = (message) {
      _addLog('📱 $message');
    };
    _lockService.setNotificationListener(_notificationListener);

    // Auto-start Auto Mode
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _restoreState();

      // Start Auto Mode if not already running
      final isRunning = await _lockService.isBackgroundServiceRunning();
      if (!isRunning && !_autoModeStarted) {
        _addLog('🤖 Auto-starting Auto Mode...');
        await _startAutoMode();
      }
    });
  }

  Future<void> _startAutoMode() async {
    try {
      await _lockService.startBackgroundService(
        serialNumber: _serialNumberController.text,
        deviceId: _deviceIdController.text,
        name: _nameController.text,
        enableAutoActions: _smartActionsEnabled,
      );
      setState(() {
        _autoModeStarted = true;
      });
      final actionsStatus = _smartActionsEnabled ? 'Smart Actions ON' : 'Smart Actions OFF';
      _addLog('✅ Auto Mode started - $actionsStatus');
    } catch (e) {
      _addLog('❌ Failed to start Auto Mode: $e');
    }
  }

  Future<void> _restoreState() async {
    try {
      _addLog('🔄 Checking service status...');
      final isRunning = await _lockService.isBackgroundServiceRunning();

      if (isRunning) {
        _addLog('🔄 Service running - syncing state...');
        await _lockService.requestStateSync();
      } else {
        _addLog('⚠️ Service not running');
      }
    } catch (e) {
      _addLog('❌ Error: $e');
    }
  }

  void _updateCirclePositionBasedOnState() {
    if (!_isConnected) {
      _updateCirclePosition(0.5); // Center when not connected
      _stopOperationAnimation();
      return;
    }

    // Cancel any pending debounce timer
    _stateUpdateDebounceTimer?.cancel();

    // Add small delay to avoid visual glitch when circle position changes
    _stateUpdateDebounceTimer = Timer(const Duration(milliseconds: 150), () {
      // Map lock states to circle positions
      final stateLower = _lockState.toLowerCase();

      // LOCK_CLOSING state (in progress): stay LEFT + show animation (counterclockwise)
      if (stateLower == 'lock_closing') {
        _addLog('🔴 Circle → LEFT (lock_closing - operation in progress)');
        _rotateClockwise = false; // Counterclockwise for lock
        _startOperationAnimation();
        _updateCirclePosition(0.0); // Left
      }
      // LOCK_CLOSED state (final): LEFT, no animation
      else if (stateLower == 'lock_closed') {
        _addLog('🔴 Circle → LEFT (lock_closed - final)');
        _stopOperationAnimation();
        _updateCirclePosition(0.0); // Left
      }
      // LOCK_OPENING states (in progress): stay RIGHT + show animation (clockwise)
      // Handles: lock_opening, lock_opening with pull, lock_opening with spring pull
      else if (stateLower.startsWith('lock_opening')) {
        _addLog('🟡 Circle → RIGHT (lock_opening* - operation in progress)');
        _rotateClockwise = true; // Clockwise for unlock
        _startOperationAnimation();
        _updateCirclePosition(1.0); // Right
      }
      // LOCK_OPENED state (final): CENTER, no animation
      else if (stateLower == 'lock_opened') {
        _addLog('🟡 Circle → CENTER (lock_opened - final)');
        _stopOperationAnimation();
        _updateCirclePosition(0.5); // Center
      }
      // LOCK_SPRING_PULL state: RIGHT + show animation (clockwise)
      else if (stateLower.contains('lock_spring_pull') || stateLower.contains('spring_pull')) {
        _addLog('🟢 Circle → RIGHT (lock_spring_pull - spring pull)');
        _rotateClockwise = true; // Clockwise for pull spring
        _startOperationAnimation();
        _updateCirclePosition(1.0); // Right
      }
      // Fallback for any other states containing these keywords
      else if (stateLower.contains('closed') && !stateLower.contains('open')) {
        _addLog('🔴 Circle → LEFT (contains "closed")');
        _stopOperationAnimation();
        _updateCirclePosition(0.0); // Left
      }
      else if (stateLower.contains('opened') || stateLower.contains('open')) {
        _addLog('🟡 Circle → CENTER (contains "opened"/"open")');
        _stopOperationAnimation();
        _updateCirclePosition(0.5); // Center
      }
      // Unknown state - stay center
      else {
        _addLog('⚫ Circle → CENTER (unknown: "$_lockState")');
        _stopOperationAnimation();
        _updateCirclePosition(0.5); // Center for unknown
      }
    });
  }

  void _updateCirclePosition(double position) {
    setState(() {
      _targetCirclePosition = position;
      _currentDragPosition = position;
    });

    _circlePositionAnimation = Tween<double>(
      begin: _circlePositionAnimation.value,
      end: position,
    ).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeInOut),
    );

    _animationController.forward(from: 0.0);
  }

  void _startOperationAnimation() {
    if (!_operationAnimationController.isAnimating) {
      _operationAnimationController.repeat(); // Infinite rotation
    }
  }

  void _stopOperationAnimation() {
    if (_operationAnimationController.isAnimating) {
      _operationAnimationController.stop();
      _operationAnimationController.reset();
    }
  }

  Color _getBackgroundColor() {
    if (!_isConnected) {
      return const Color(0xFF6B7280); // Gray for not connected
    }

    switch (_lockState.toLowerCase()) {
      case 'locked':
      case 'locking':
      case 'unlocked':
      case 'unlocking':
        return const Color(0xFF0F172A); // Dark blue
      case 'pull_spring':
      case 'pulling':
        return const Color(0xFF14B8A6); // Teal/green
      default:
        return const Color(0xFF0F172A); // Dark blue default
    }
  }

  Color _getCircleColor() {
    if (!_isConnected) {
      return const Color(0xFF9CA3AF); // Light gray for not connected
    }
    return const Color(0xFF3B82F6); // Blue for connected
  }

  void _onPanStart(DragStartDetails details) {
    setState(() {
      _isDragging = true;
      _dragStartX = details.localPosition.dx;
    });
  }

  void _onPanUpdate(DragUpdateDetails details, double screenWidth) {
    final double circleDiameter = 100.0;
    final double maxDragDistance = screenWidth - circleDiameter - 32; // Account for padding

    setState(() {
      double dragDistance = details.localPosition.dx - _dragStartX;
      double startPosition = _targetCirclePosition * maxDragDistance;
      double newPosition = (startPosition + dragDistance) / maxDragDistance;
      _currentDragPosition = newPosition.clamp(0.0, 1.0);
    });
  }

  void _onPanEnd(DragEndDetails details, double screenWidth) {
    setState(() {
      _isDragging = false;
    });

    // Determine which action to take based on drag direction and current state
    double dragDelta = _currentDragPosition - _targetCirclePosition;

    if (dragDelta.abs() < 0.2) {
      // Snap back if drag wasn't significant enough
      _updateCirclePosition(_targetCirclePosition);
      return;
    }

    // Perform action based on current state and drag direction
    if (!_isConnected) {
      _updateCirclePosition(_targetCirclePosition); // Snap back if not connected
      return;
    }

    if (dragDelta > 0) {
      // Swiped RIGHT
      _handleSwipeRight();
    } else {
      // Swiped LEFT
      _handleSwipeLeft();
    }
  }

  Future<void> _handleSwipeRight() async {
    final stateLower = _lockState.toLowerCase();

    // From LOCKED (left) → swipe RIGHT to UNLOCK
    if ((stateLower.contains('locked') || stateLower.contains('closed')) &&
        !stateLower.contains('unlocked') &&
        !stateLower.contains('open')) {
      _addLog('🔓 Swipe RIGHT from LOCKED → Opening lock');
      // Move circle to RIGHT immediately (clockwise rotation for unlock)
      _rotateClockwise = true;
      _startOperationAnimation();
      _updateCirclePosition(1.0);
      await _lockService.openLock();
    }
    // From UNLOCKED (center) → swipe RIGHT to PULL SPRING
    else if (stateLower.contains('unlocked') || stateLower.contains('open')) {
      _addLog('🔧 Swipe RIGHT from UNLOCKED → Pull spring');
      // Move circle to RIGHT immediately (clockwise rotation for pull spring)
      _rotateClockwise = true;
      _startOperationAnimation();
      _updateCirclePosition(1.0);
      await _lockService.pullSpring();
    }
    else {
      // Unknown state - snap back
      _addLog('⚠️ Swipe RIGHT from unknown state "$_lockState" - ignoring');
      _updateCirclePosition(_targetCirclePosition);
    }
  }

  Future<void> _handleSwipeLeft() async {
    final stateLower = _lockState.toLowerCase();

    // From UNLOCKED (center) → swipe LEFT to LOCK
    if (stateLower.contains('unlocked') || stateLower.contains('open')) {
      _addLog('🔒 Swipe LEFT from UNLOCKED → Closing lock');
      // Move circle to LEFT immediately (counterclockwise rotation for lock)
      _rotateClockwise = false;
      _startOperationAnimation();
      _updateCirclePosition(0.0);
      await _lockService.closeLock();
    }
    // From PULL SPRING (right) → swipe LEFT to LOCK
    else if (stateLower.contains('pull') || stateLower.contains('spring')) {
      _addLog('🔒 Swipe LEFT from PULL SPRING → Closing lock');
      // Move circle to LEFT immediately (counterclockwise rotation for lock)
      _rotateClockwise = false;
      _startOperationAnimation();
      _updateCirclePosition(0.0);
      await _lockService.closeLock();
    }
    else {
      // Unknown state or already locked - snap back
      _addLog('⚠️ Swipe LEFT from state "$_lockState" - ignoring');
      _updateCirclePosition(_targetCirclePosition);
    }
  }

  // Manual connect/disconnect methods
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
          _addLog('✅ Connected to lock');
        }
      });
    } catch (e) {
      setState(() {
        _isConnecting = false;
        _addLog('❌ Connection failed: $e');
      });
    }
  }

  Future<void> _disconnect() async {
    try {
      await _lockService.disconnect();
      setState(() {
        _isConnected = false;
        _addLog('🔌 Disconnected from lock');
      });
    } catch (e) {
      _addLog('❌ Disconnect failed: $e');
    }
  }

  Future<void> _getLockState() async {
    try {
      final result = await _lockService.getLockState();
      _addLog('📊 Lock State: $result');
    } catch (e) {
      _addLog('❌ Get state failed: $e');
    }
  }

  Future<void> _getBattery() async {
    try {
      final result = await _lockService.getBattery();
      _addLog('🔋 $result');
    } catch (e) {
      _addLog('❌ Get battery failed: $e');
    }
  }

  Future<void> _getFirmwareVersion() async {
    try {
      final result = await _lockService.getFirmwareVersion();
      _addLog('📱 Firmware Version: $result');
    } catch (e) {
      _addLog('❌ Get firmware failed: $e');
    }
  }

  Future<void> _getSignedTime() async {
    try {
      final result = await _lockService.getSignedTime();
      _addLog('🕐 Signed Time: $result');
    } catch (e) {
      _addLog('❌ Get signed time failed: $e');
    }
  }

  Future<void> _getActivityLogs() async {
    _addLog('📋 Downloading activity logs...');

    try {
      final result = await _lockService.getActivityLogs();
      _addLog(result);
    } catch (e) {
      _addLog('❌ Failed to download logs: $e');
    }
  }

  Future<void> _stopAutoMode() async {
    try {
      await _lockService.stopBackgroundService();
      setState(() {
        _autoModeStarted = false;
        _smartActionsEnabled = false;
      });
      _addLog('⏹️ Auto Mode stopped');
    } catch (e) {
      _addLog('❌ Failed to stop Auto Mode: $e');
    }
  }

  Future<void> _toggleSmartActions(bool value) async {
    setState(() {
      _smartActionsEnabled = value;
    });
    // Restart service with new setting
    await _stopAutoMode();
    await _startAutoMode();
  }

  @override
  void dispose() {
    // Cancel debounce timer
    _stateUpdateDebounceTimer?.cancel();

    // Dispose controllers
    _serialNumberController.dispose();
    _deviceIdController.dispose();
    _nameController.dispose();

    // Remove listeners to prevent memory leaks
    _lockService.removeConnectionStateListener(_connectionListener);
    _lockService.removeLockStateListener(_stateListener);
    _lockService.removeNotificationListener(_notificationListener);

    _animationController.dispose();
    _operationAnimationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;

    final circleDiameter = 100.0;
    final horizontalPadding = 16.0;
    final maxDragDistance = screenWidth - circleDiameter - (horizontalPadding * 2);

    // Calculate circle position
    double circleLeft;
    if (_isDragging) {
      circleLeft = horizontalPadding + (_currentDragPosition * maxDragDistance);
    } else {
      circleLeft = horizontalPadding + (_circlePositionAnimation.value * maxDragDistance);
    }

    return Scaffold(
      backgroundColor: _getBackgroundColor(),
      body: Stack(
        children: [
          // Main background with circle
          AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  _getBackgroundColor(),
                  _getBackgroundColor().withOpacity(0.8),
                ],
              ),
            ),
            child: Stack(
              children: [
                // Main draggable circle
                Positioned(
                  left: circleLeft,
                  top: screenHeight * 0.4 - (circleDiameter / 2),
                  child: GestureDetector(
                    onPanStart: _onPanStart,
                    onPanUpdate: (details) => _onPanUpdate(details, screenWidth),
                    onPanEnd: (details) => _onPanEnd(details, screenWidth),
                    child: Container(
                      width: circleDiameter,
                      height: circleDiameter,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: _getCircleColor(),
                        boxShadow: [
                          BoxShadow(
                            color: _getCircleColor().withOpacity(0.4),
                            blurRadius: 20,
                            spreadRadius: 5,
                          ),
                        ],
                      ),
                      child: _isConnected && _lockState.toLowerCase() == 'unlocked'
                          ? Container(
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                border: Border.all(
                                  color: Colors.white.withOpacity(0.3),
                                  width: 2,
                                ),
                              ),
                              margin: const EdgeInsets.all(12),
                            )
                          : null,
                    ),
                  ),
                ),

                // Operation indicator: spinning dot around the circle
                if (_lockState.toLowerCase() == 'lock_closing' ||
                    _lockState.toLowerCase().startsWith('lock_opening') ||
                    _lockState.toLowerCase().contains('spring_pull'))
                  Positioned(
                    left: circleLeft,
                    top: screenHeight * 0.4 - (circleDiameter / 2),
                    child: SizedBox(
                      width: circleDiameter,
                      height: circleDiameter,
                      child: Transform.rotate(
                        angle: (_rotateClockwise ? 1 : -1) * _operationRotationAnimation.value * 2 * 3.14159,
                        child: Align(
                          alignment: Alignment.topCenter,
                          child: Container(
                            width: 16,
                            height: 16,
                            margin: const EdgeInsets.only(top: 4),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: Colors.white,
                              boxShadow: [
                                BoxShadow(
                                  color: Colors.white.withOpacity(0.8),
                                  blurRadius: 12,
                                  spreadRadius: 3,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),

                // Guide ring for unlocked state
                if (_isConnected && _lockState.toLowerCase() == 'unlocked' && !_isDragging)
                  Positioned(
                    left: screenWidth / 2 - 80,
                    top: screenHeight * 0.4 - 80,
                    child: Container(
                      width: 160,
                      height: 160,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: Colors.white.withOpacity(0.2),
                          width: 1,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),

          // Draggable bottom sheet with settings
          DraggableScrollableSheet(
            initialChildSize: 0.08, // Start minimized (showing only handle)
            minChildSize: 0.08,
            maxChildSize: 0.9, // Can expand to 90% of screen
            builder: (BuildContext context, ScrollController scrollController) {
              return Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(24),
                    topRight: Radius.circular(24),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.2),
                      blurRadius: 10,
                      spreadRadius: 2,
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    // Drag handle
                    Container(
                      margin: const EdgeInsets.only(top: 12, bottom: 8),
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: Colors.grey[300],
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),

                    // Settings content
                    Expanded(
                      child: _buildSettingsContent(scrollController),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsContent(ScrollController scrollController) {
    return SingleChildScrollView(
      controller: scrollController,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Auto Mode Section
            Card(
              color: _autoModeStarted ? Colors.deepPurple[50] : null,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    SwitchListTile(
                      title: const Text(
                        '🤖 Auto Mode',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      subtitle: Text(
                        _autoModeStarted
                            ? 'Status: $_lockState'
                            : 'Keep connection alive in background',
                        style: const TextStyle(fontSize: 13),
                      ),
                      value: _autoModeStarted,
                      onChanged: (value) async {
                        if (value) {
                          await _startAutoMode();
                        } else {
                          await _stopAutoMode();
                        }
                      },
                      activeColor: Colors.deepPurple,
                    ),
                    if (_autoModeStarted) ...[
                      const Divider(),
                      Padding(
                        padding: const EdgeInsets.only(left: 16.0),
                        child: SwitchListTile(
                          title: const Text(
                            '⚡ Smart Actions',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          subtitle: Text(
                            _smartActionsEnabled
                                ? '🔒 CLOSED → OPEN  |  🔓 OPEN → PULL + CLOSE'
                                : 'Manual control only',
                            style: const TextStyle(fontSize: 12),
                          ),
                          value: _smartActionsEnabled,
                          onChanged: _toggleSmartActions,
                          activeColor: Colors.orange,
                        ),
                      ),
                    ],
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
                          onPressed: _isConnecting || _isConnected ? null : _connect,
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
                            onPressed: _isConnected
                                ? () => _lockService.openLock()
                                : null,
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
                            onPressed: _isConnected
                                ? () => _lockService.closeLock()
                                : null,
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
                            onPressed: _isConnected
                                ? () => _lockService.pullSpring()
                                : null,
                            icon: const Icon(Icons.settings_input_component),
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
                            onPressed: _isConnected ? _getBattery : null,
                            icon: const Icon(Icons.battery_std),
                            label: const Text('Battery'),
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
                            onPressed: _isConnected ? _getFirmwareVersion : null,
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

            const SizedBox(height: 16),

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
                      enabled: !_isConnected && !_autoModeStarted,
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
                      enabled: !_isConnected && !_autoModeStarted,
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _nameController,
                      decoration: const InputDecoration(
                        labelText: 'Lock Name',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.label),
                      ),
                      enabled: !_isConnected && !_autoModeStarted,
                    ),
                    const SizedBox(height: 16),
                    SwitchListTile(
                      title: const Text('Keep Connection'),
                      subtitle: const Text('Maintain indefinite connection to lock'),
                      value: _keepConnection,
                      onChanged: (_isConnected || _autoModeStarted)
                          ? null
                          : (value) {
                              setState(() {
                                _keepConnection = value;
                              });
                            },
                      activeColor: const Color(0xFF22345a),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 16),

            // Messages Log
            Card(
              color: Colors.black87,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.grey[900],
                      borderRadius: const BorderRadius.only(
                        topLeft: Radius.circular(12),
                        topRight: Radius.circular(12),
                      ),
                    ),
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
                  Container(
                    height: 200,
                    padding: const EdgeInsets.all(8),
                    child: _logs.isEmpty
                        ? const Center(
                            child: Text(
                              'No messages yet',
                              style: TextStyle(color: Colors.grey),
                            ),
                          )
                        : ListView.builder(
                            itemCount: _logs.length,
                            itemBuilder: (context, index) {
                              return Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 4,
                                ),
                                child: Text(
                                  _logs[index],
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

            const SizedBox(height: 32), // Extra padding at bottom
          ],
        ),
      ),
    );
  }
}
