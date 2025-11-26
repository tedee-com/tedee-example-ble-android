import 'package:flutter/material.dart';
import '../services/tedee_lock_service.dart';
import 'lock_control_screen.dart';

class SimpleLockScreen extends StatefulWidget {
  const SimpleLockScreen({super.key});

  @override
  State<SimpleLockScreen> createState() => _SimpleLockScreenState();
}

class _SimpleLockScreenState extends State<SimpleLockScreen> with TickerProviderStateMixin {
  final TedeeLockService _lockService = TedeeLockService();

  bool _isConnected = false;
  String _lockState = "Unknown";
  bool _autoModeStarted = false;
  final List<String> _logs = [];

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
      _addLog('🔔 Lock State: $lockState');
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
        serialNumber: '10530206-030484',
        deviceId: '273450',
        name: 'Lock-40C5',
        enableAutoActions: false, // Manual control only
      );
      setState(() {
        _autoModeStarted = true;
      });
      _addLog('✅ Auto Mode started');
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

    // Map lock states to circle positions
    final stateLower = _lockState.toLowerCase();

    // LOCKING state (in progress): stay LEFT + show animation
    if (stateLower == 'locking') {
      _addLog('🔴 Circle → LEFT (locking - operation in progress)');
      _startOperationAnimation();
      _updateCirclePosition(0.0); // Left
    }
    // LOCKED state (final): LEFT, no animation
    else if ((stateLower.contains('locked') || stateLower.contains('closed')) &&
        !stateLower.contains('unlocked') &&
        !stateLower.contains('open')) {
      _addLog('🔴 Circle → LEFT (locked/closed - final)');
      _stopOperationAnimation();
      _updateCirclePosition(0.0); // Left
    }
    // UNLOCKING state (in progress): stay RIGHT + show animation
    else if (stateLower == 'unlocking') {
      _addLog('🟡 Circle → RIGHT (unlocking - operation in progress)');
      _startOperationAnimation();
      _updateCirclePosition(1.0); // Right
    }
    // UNLOCKED state (final): CENTER, no animation
    else if (stateLower.contains('unlocked') ||
             stateLower.contains('open')) {
      _addLog('🟡 Circle → CENTER (unlocked/open - final)');
      _stopOperationAnimation();
      _updateCirclePosition(0.5); // Center
    }
    // PULLING state (in progress): show animation
    else if (stateLower == 'pulling') {
      _addLog('🟢 Circle → RIGHT (pulling - operation in progress)');
      _startOperationAnimation();
      _updateCirclePosition(1.0); // Right
    }
    // PULL SPRING state (final): RIGHT, no animation
    else if (stateLower.contains('pull') || stateLower.contains('spring')) {
      _addLog('🟢 Circle → RIGHT (pull spring - final)');
      _stopOperationAnimation();
      _updateCirclePosition(1.0); // Right
    }
    // Unknown state - stay center
    else {
      _addLog('⚫ Circle → CENTER (unknown: "$_lockState")');
      _stopOperationAnimation();
      _updateCirclePosition(0.5); // Center for unknown
    }
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
      await _lockService.openLock();
    }
    // From UNLOCKED (center) → swipe RIGHT to PULL SPRING
    else if (stateLower.contains('unlocked') || stateLower.contains('open')) {
      _addLog('🔧 Swipe RIGHT from UNLOCKED → Pull spring');
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
      await _lockService.closeLock();
    }
    // From PULL SPRING (right) → swipe LEFT to LOCK
    else if (stateLower.contains('pull') || stateLower.contains('spring')) {
      _addLog('🔒 Swipe LEFT from PULL SPRING → Closing lock');
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
      body: Column(
        children: [
          // Main UI with circle
          Expanded(
            child: AnimatedContainer(
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
              top: screenHeight / 2 - (circleDiameter / 2),
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
            if (_lockState.toLowerCase() == 'locking' ||
                _lockState.toLowerCase() == 'unlocking' ||
                _lockState.toLowerCase() == 'pulling')
              Positioned(
                left: circleLeft,
                top: screenHeight / 2 - (circleDiameter / 2),
                child: SizedBox(
                  width: circleDiameter,
                  height: circleDiameter,
                  child: Transform.rotate(
                    angle: _operationRotationAnimation.value * 2 * 3.14159, // Full rotation
                    child: Align(
                      alignment: Alignment.topCenter,
                      child: Container(
                        width: 16, // Increased size for better visibility
                        height: 16,
                        margin: const EdgeInsets.only(top: 4), // Closer to circle edge
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Colors.yellow, // Bright yellow for visibility
                          boxShadow: [
                            BoxShadow(
                              color: Colors.yellow.withOpacity(0.8),
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

            // Guide ring for unlocked state (shows swipe options)
            if (_isConnected && _lockState.toLowerCase() == 'unlocked' && !_isDragging)
              Positioned(
                left: screenWidth / 2 - 80,
                top: screenHeight / 2 - 80,
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

            // Button to access advanced controls (bottom right)
            Positioned(
              bottom: 32,
              right: 32,
              child: FloatingActionButton(
                onPressed: () async {
                  _addLog('⚙️ Opening advanced controls...');
                  await Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const LockControlScreen(),
                    ),
                  );
                  _addLog('⚙️ Returned from advanced controls');
                  // Refresh state when returning from settings
                  await _restoreState();
                },
                backgroundColor: Colors.white.withOpacity(0.2),
                child: const Icon(Icons.settings, color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    ),

          // Log viewer at bottom
          Container(
            height: 150,
            color: Colors.black87,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  color: Colors.grey[900],
                  child: Row(
                    children: [
                      const Icon(Icons.terminal, color: Colors.white, size: 16),
                      const SizedBox(width: 8),
                      const Text(
                        'Debug Log',
                        style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                      const Spacer(),
                      Text(
                        'Connected: ${_isConnected ? "✅" : "❌"} | State: $_lockState',
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 10,
                        ),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: _logs.isEmpty
                      ? const Center(
                          child: Text(
                            'No logs yet',
                            style: TextStyle(color: Colors.grey, fontSize: 11),
                          ),
                        )
                      : ListView.builder(
                          itemCount: _logs.length,
                          itemBuilder: (context, index) {
                            return Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                border: Border(
                                  bottom: BorderSide(
                                    color: Colors.grey[800]!,
                                    width: 0.5,
                                  ),
                                ),
                              ),
                              child: Text(
                                _logs[index],
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontFamily: 'monospace',
                                  fontSize: 10,
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
