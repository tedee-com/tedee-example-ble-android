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
  bool _autoOpenEnabled = false; // Auto open: automatically open lock when closed
  String _lockState = "Unknown";
  int _batteryLevel = 0; // Battery level 0-100
  bool _isCharging = false; // Charging status
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

    // Auto-start service (always on)
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _restoreState();

      // Start service if not already running (Auto Mode is always on)
      final isRunning = await _lockService.isBackgroundServiceRunning();
      if (!isRunning) {
        _addLog('🤖 Starting service...');
        await _startService();
      }

      // Get initial battery level
      await _updateBatteryLevel();
    });
  }

  Future<void> _startService() async {
    try {
      await _lockService.startBackgroundService(
        serialNumber: _serialNumberController.text,
        deviceId: _deviceIdController.text,
        name: _nameController.text,
        enableAutoActions: _autoOpenEnabled,
      );
      final actionsStatus = _autoOpenEnabled ? 'Auto Open ON' : 'Auto Open OFF';
      _addLog('✅ Service started - $actionsStatus');
    } catch (e) {
      _addLog('❌ Failed to start service: $e');
    }
  }

  Future<void> _updateBatteryLevel() async {
    try {
      final result = await _lockService.getBattery();
      // Parse battery result: "🔋 Battery: 85% - ⚡ Charging"
      final batteryMatch = RegExp(r'Battery: (\d+)%').firstMatch(result);
      if (batteryMatch != null) {
        setState(() {
          _batteryLevel = int.parse(batteryMatch.group(1)!);
          _isCharging = result.contains('Charging');
        });
      }
    } catch (e) {
      // Ignore battery errors
    }
  }

  Future<void> _toggleAutoOpen(bool value) async {
    setState(() {
      _autoOpenEnabled = value;
    });
    // Restart service with new setting
    try {
      await _lockService.stopBackgroundService();
      await _startService();
    } catch (e) {
      _addLog('❌ Failed to update Auto Open: $e');
    }
  }

  Future<void> _restoreState() async {
    try {
      _addLog('🔄 Checking service status...');
      final isRunning = await _lockService.isBackgroundServiceRunning();

      if (isRunning) {
        _addLog('🔄 Service running - syncing state...');
        await _lockService.requestStateSync();
        // Update battery level
        await _updateBatteryLevel();
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
                  top: screenHeight * 0.33 - (circleDiameter / 2),
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
                    top: screenHeight * 0.33 - (circleDiameter / 2),
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
                    top: screenHeight * 0.33 - 80,
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
                  color: _getBackgroundColor(),
                  border: Border.all(
                    color: Colors.white.withOpacity(0.5),
                    width: 2,
                  ),
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(24),
                    topRight: Radius.circular(24),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.3),
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
                        color: Colors.white.withOpacity(0.5),
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
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Battery Level Display
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: _getBackgroundColor().withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: Colors.white.withOpacity(0.3),
                  width: 2,
                ),
              ),
              child: Column(
                children: [
                  Icon(
                    _isCharging ? Icons.battery_charging_full : Icons.battery_std,
                    size: 48,
                    color: _batteryLevel > 20 ? Colors.green : Colors.red,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    '$_batteryLevel%',
                    style: const TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _isCharging ? '⚡ Charging' : 'Battery Level',
                    style: TextStyle(
                      fontSize: 14,
                      color: Colors.white.withOpacity(0.7),
                    ),
                  ),
                  if (_isConnected) ...[
                    const SizedBox(height: 16),
                    TextButton.icon(
                      onPressed: _updateBatteryLevel,
                      icon: const Icon(Icons.refresh, size: 16),
                      label: const Text('Refresh'),
                      style: TextButton.styleFrom(
                        foregroundColor: Colors.white.withOpacity(0.9),
                      ),
                    ),
                  ],
                ],
              ),
            ),

            const SizedBox(height: 24),

            // Auto Open Toggle
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: _getBackgroundColor().withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: Colors.white.withOpacity(0.3),
                  width: 2,
                ),
              ),
              child: SwitchListTile(
                title: const Text(
                  'Auto Open',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                subtitle: Text(
                  _autoOpenEnabled
                      ? 'Automatically open lock when closed'
                      : 'Manual control only',
                  style: TextStyle(
                    fontSize: 13,
                    color: Colors.white.withOpacity(0.7),
                  ),
                ),
                value: _autoOpenEnabled,
                onChanged: _toggleAutoOpen,
                activeColor: Colors.orange,
              ),
            ),

            const SizedBox(height: 24),

            // Messages Log
            Container(
              decoration: BoxDecoration(
                color: _getBackgroundColor().withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: Colors.white.withOpacity(0.3),
                  width: 2,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.2),
                      borderRadius: const BorderRadius.only(
                        topLeft: Radius.circular(14),
                        topRight: Radius.circular(14),
                      ),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.message, color: Colors.white, size: 18),
                        SizedBox(width: 8),
                        Text(
                          'Messages Log',
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    height: 250,
                    padding: const EdgeInsets.all(12),
                    child: _logs.isEmpty
                        ? Center(
                            child: Text(
                              'No messages yet',
                              style: TextStyle(
                                color: Colors.white.withOpacity(0.5),
                              ),
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
                                    fontSize: 11,
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
