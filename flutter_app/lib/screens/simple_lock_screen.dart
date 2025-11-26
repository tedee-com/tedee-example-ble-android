import 'package:flutter/material.dart';
import '../services/tedee_lock_service.dart';
import 'lock_control_screen.dart';

class SimpleLockScreen extends StatefulWidget {
  const SimpleLockScreen({super.key});

  @override
  State<SimpleLockScreen> createState() => _SimpleLockScreenState();
}

class _SimpleLockScreenState extends State<SimpleLockScreen> with SingleTickerProviderStateMixin {
  final TedeeLockService _lockService = TedeeLockService();

  bool _isConnected = false;
  String _lockState = "Unknown";

  // Animation controller for smooth circle movement
  late AnimationController _animationController;
  late Animation<double> _circlePositionAnimation;

  // Circle position: 0.0 = left, 0.5 = center, 1.0 = right
  double _targetCirclePosition = 0.5;

  // Drag tracking
  double _dragStartX = 0.0;
  double _currentDragPosition = 0.5;
  bool _isDragging = false;

  @override
  void initState() {
    super.initState();

    // Initialize animation controller
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

    // Listen for connection state changes
    _lockService.setConnectionStateListener((isConnected) {
      setState(() {
        _isConnected = isConnected;
        if (!isConnected) {
          _lockState = "Unknown";
          _updateCirclePosition(0.5); // Center when disconnected
        }
      });
    });

    // Listen for lock state changes
    _lockService.setLockStateListener((lockState) {
      setState(() {
        _lockState = lockState;
        _updateCirclePositionBasedOnState();
      });
    });

    // Restore state from background service if running
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _restoreState();
    });
  }

  Future<void> _restoreState() async {
    try {
      final isRunning = await _lockService.isBackgroundServiceRunning();
      if (isRunning) {
        await _lockService.requestStateSync();
      }
    } catch (e) {
      // Ignore errors during state restoration
    }
  }

  void _updateCirclePositionBasedOnState() {
    if (!_isConnected) {
      _updateCirclePosition(0.5); // Center when not connected
      return;
    }

    // Map lock states to circle positions
    switch (_lockState.toLowerCase()) {
      case 'locked':
      case 'locking':
        _updateCirclePosition(0.0); // Left
        break;
      case 'unlocked':
      case 'unlocking':
        _updateCirclePosition(0.5); // Center
        break;
      case 'pull_spring':
      case 'pulling':
        _updateCirclePosition(1.0); // Right
        break;
      default:
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
    switch (_lockState.toLowerCase()) {
      case 'locked':
      case 'locking':
        // From locked (left) → swipe right to unlock
        await _lockService.openLock();
        break;
      case 'unlocked':
      case 'unlocking':
        // From unlocked (center) → swipe right to pull spring
        await _lockService.pullSpring();
        break;
      default:
        // Snap back
        _updateCirclePosition(_targetCirclePosition);
    }
  }

  Future<void> _handleSwipeLeft() async {
    switch (_lockState.toLowerCase()) {
      case 'unlocked':
      case 'unlocking':
        // From unlocked (center) → swipe left to lock
        await _lockService.closeLock();
        break;
      case 'pull_spring':
      case 'pulling':
        // From pull spring (right) → swipe left to lock
        await _lockService.closeLock();
        break;
      default:
        // Snap back
        _updateCirclePosition(_targetCirclePosition);
    }
  }

  @override
  void dispose() {
    _animationController.dispose();
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
      body: AnimatedContainer(
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
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const LockControlScreen(),
                    ),
                  );
                },
                backgroundColor: Colors.white.withOpacity(0.2),
                child: const Icon(Icons.settings, color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
