# Tedee Lock Flutter App

Flutter application for controlling Tedee smart locks using Platform Channels to communicate with the native Tedee Android SDK.

## Project Structure

```
flutter_app/
├── lib/
│   ├── main.dart                          # Flutter app entry point
│   ├── services/
│   │   └── tedee_lock_service.dart        # MethodChannel service (Dart ↔ Kotlin)
│   └── screens/
│       └── lock_control_screen.dart       # Lock control UI
│
├── android/
│   └── app/src/main/kotlin/com/tedee/flutter/
│       ├── MainActivity.kt                # Flutter activity with MethodChannel handler
│       └── TedeeFlutterBridge.kt         # Bridge to Tedee SDK
│
├── pubspec.yaml                           # Flutter dependencies
└── README.md                              # This file
```

## Architecture

```
┌─────────────────────────────────────┐
│        FLUTTER (Dart)               │
│  - UI (lock_control_screen.dart)   │
│  - Service (tedee_lock_service.dart)│
└──────────────┬──────────────────────┘
               │
         MethodChannel
    'com.tedee.flutter/lock'
               │
┌──────────────▼──────────────────────┐
│    ANDROID NATIVE (Kotlin)          │
│  - MainActivity.kt                  │
│  - TedeeFlutterBridge.kt           │
│  - Tedee Android SDK                │
└─────────────────────────────────────┘
```

## Prerequisites

1. **Flutter SDK** installed (https://docs.flutter.dev/get-started/install)
2. **Android Studio** with:
   - Android SDK 26+ (Android 8.0+)
   - Android SDK 35 (for compilation)
   - Java/JDK 17
3. **Physical Android device** (BLE not supported in emulators)
4. **Tedee Personal Access Key** from portal.tedee.com

## Setup Instructions

### 1. Install Flutter

```bash
# Verify Flutter installation
flutter doctor
```

### 2. Install Dependencies

```bash
cd flutter_app
flutter pub get
```

### 3. Create local.properties

Create `android/local.properties` with:
```properties
sdk.dir=/path/to/your/Android/sdk
flutter.sdk=/path/to/your/flutter/sdk
```

### 4. Configure Lock Credentials

Edit `lib/screens/simple_lock_screen.dart` and update the default lock credentials:

```dart
final TextEditingController _serialNumberController =
    TextEditingController(text: 'YOUR-SERIAL-NUMBER');
final TextEditingController _deviceIdController =
    TextEditingController(text: 'YOUR-DEVICE-ID');
final TextEditingController _nameController =
    TextEditingController(text: 'YOUR-LOCK-NAME');
```

You can find these values in the official Tedee app:
- **Serial Number**: Lock > Settings > Information > Serial number
- **Device ID**: Lock > Settings > Information > Device ID
- **Lock Name**: Lock > Settings > Lock name

### 5. Configure Personal Access Key

The app uses a Personal Access Key for Tedee API authentication. Configure it in:

`android/app/src/main/kotlin/com/tedee/flutter/Constants.kt`

```kotlin
object Constants {
    const val PERSONAL_ACCESS_KEY: String = "YOUR-KEY-HERE"
}
```

Get your Personal Access Key from:
1. Log in to https://portal.tedee.com
2. Click on your initials (top right)
3. Navigate to "Personal Access Keys"
4. Generate key with **Device certificates - Read** scope

## Running the App

### Connect Android Device

```bash
# Enable USB debugging on your Android device
# Connect via USB and verify connection
flutter devices
```

### Run App

```bash
cd flutter_app
flutter run
```

## MethodChannel API Reference

### Core Methods (Dart → Kotlin)

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `connect` | serialNumber, deviceId, name, keepConnection | bool | Connect to lock with automatic certificate generation |
| `disconnect` | - | void | Disconnect from lock |

### Lock Commands

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `openLock` | - | String | Unlock (BLE command 0x51) |
| `closeLock` | - | String | Lock (BLE command 0x50) |
| `pullSpring` | - | String | Pull spring (BLE command 0x52) |
| `getLockState` | - | String | Get current lock state (returns state + jam status) |
| `getBattery` | - | String | Get battery level and charging status |
| `getFirmwareVersion` | - | String | Get lock firmware version |
| `getActivityLogs` | - | String | Download activity logs (multi-package retrieval) |

### API Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getSignedTime` | - | String | Get synchronized time from Tedee API |

### Background Service

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `startBackgroundService` | serialNumber, deviceId, name, enableAutoActions | void | Start foreground service with persistent connection |
| `stopBackgroundService` | - | void | Stop background service |
| `isBackgroundServiceRunning` | - | bool | Check if background service is running |
| `requestStateSync` | - | bool | Request state synchronization from service to UI |

### Callbacks (Kotlin → Dart)

| Callback | Data Type | Description |
|----------|-----------|-------------|
| `onNotification` | String | General lock notifications and command results |
| `onConnectionStateChanged` | bool | Connection state changes (true=connected, false=disconnected) |
| `onLockStateChanged` | String | Lock state changes (e.g., "LOCK_OPENED", "LOCK_CLOSED") |

## Configuration

The app is preconfigured with the following constants (from `../app/src/main/java/tedee/mobile/demo/Constants.kt`):

```dart
// In lock_control_screen.dart
final String _serialNumber = '10530206-030484';
final String _deviceId = '273450';
final String _name = 'Lock-40C5';
```

To change these values, edit `lib/screens/lock_control_screen.dart`.

## Troubleshooting

### BLE Permissions Error
**Error:** App crashes or can't scan for devices

**Solution:**
1. Permissions are automatically requested on app startup (BLE, Location, Notifications)
2. If denied, grant them manually in device settings: Settings > Apps > Tedee Flutter > Permissions
3. Ensure `AndroidManifest.xml` has all required permissions (already configured)

### Connection Timeout
**Error:** Connection hangs or times out

**Solution:**
1. Ensure lock is powered on and nearby
2. Check lock is not connected to another device
3. Verify serial number and device ID are correct

### Gradle Build Errors
**Error:** Compilation fails with dependency errors

**Solution:**
```bash
cd android
./gradlew clean
./gradlew build --refresh-dependencies
```

## Current Status

✅ **Fully Implemented:**
1. **Certificate generation** - Automatic generation and caching via Tedee API
2. **Runtime permissions** - BLE, Location, and Notification permissions automatically requested
3. **Connection state management** - Real-time state synchronization with BroadcastReceiver
4. **Error handling UI** - Comprehensive message log with 50-entry history
5. **Background service** - TedeeLockForegroundService maintains persistent connection
6. **Auto-actions** - Auto-open mode (automatically opens lock when it closes)
7. **Battery monitoring** - Real-time battery level with auto-refresh every 2 minutes
8. **Activity logs** - Full activity log download with multi-package support
9. **Firmware queries** - Get firmware version and device settings
10. **Advanced UI** - Swipe gesture controls with smooth animations and operation indicators

## Features Overview

### Core Lock Control
- ✅ **Secure connection** with automatic certificate management
- ✅ **Lock commands**: Open, Close, Pull Spring
- ✅ **Real-time state monitoring**: Lock state updates via listener callbacks
- ✅ **Battery status**: Live battery percentage and charging status
- ✅ **Activity logs**: Download and view lock activity history
- ✅ **Firmware info**: Query firmware version and device settings

### Background Service
- ✅ **Persistent connection**: Maintains BLE connection even when app is in background
- ✅ **Foreground service**: Displays permanent notification while connected
- ✅ **Auto-actions**: Optional auto-open mode for automatic unlocking
- ✅ **State sync**: Broadcasts connection and lock state changes to UI
- ✅ **Service persistence**: Survives app restarts and configuration changes

### User Interface
- ✅ **Swipe gestures**:
  - Swipe right from locked → unlock
  - Swipe right from unlocked → pull spring
  - Swipe left from unlocked → lock
- ✅ **Visual feedback**: Smooth circle animations with position indicators
- ✅ **Operation indicators**: Spinning dot shows active lock operations (clockwise/counterclockwise)
- ✅ **Battery display**: Auto-refreshing battery widget with charging icon
- ✅ **Message log**: Real-time event log in draggable bottom sheet
- ✅ **Connection status**: Visual indicators for connection state

### Technical Features
- ✅ **MethodChannel** for Flutter ↔ Kotlin communication
- ✅ **Coroutines** for asynchronous operations
- ✅ **DataStore** for secure certificate caching
- ✅ **Retrofit** for Tedee Cloud API calls
- ✅ **BroadcastReceiver** for service-to-UI communication
- ✅ **Timber logging** for debugging
- ✅ **ILockInteractor compatibility fix** (see FLUTTER_FIX_NOTES.md)

## Known Limitations

1. **Android only** - iOS support requires Tedee iOS SDK integration
2. **Single lock support** - SDK limitation (can only connect to one lock at a time)
3. **Custom BLE commands disabled** - Due to Kotlin/SDK compatibility (available in native Android app)

## Future Enhancements

1. **iOS support** with Tedee iOS SDK and Swift MethodChannel implementation
2. **Multiple lock support** (when SDK supports it)
3. **Lock registration flow** (add new lock to account from app)
4. **Custom BLE command input** (when Kotlin compatibility resolved)
5. **Lock settings configuration** (calibration, auto-lock, etc.)
6. **Push notifications** for lock state changes

## Development

### Hot Reload

Flutter supports hot reload for UI changes:
```bash
# While app is running, press 'r' in terminal for hot reload
# Press 'R' for hot restart
```

**Note:** Hot reload doesn't work for native Android code changes. You must rebuild the app:
```bash
flutter run
```

### Debugging

**Flutter DevTools:**
```bash
flutter pub global activate devtools
flutter pub global run devtools
```

**Native Android Logs:**
```bash
adb logcat | grep -i tedee
```

## Testing

### Unit Tests (TODO)

```bash
flutter test
```

### Integration Tests (TODO)

Requires physical device with actual Tedee lock.

## Building for Release

```bash
# Build APK
flutter build apk --release

# Build App Bundle (for Play Store)
flutter build appbundle --release
```

**Output:**
- APK: `build/app/outputs/flutter-apk/app-release.apk`
- AAB: `build/app/outputs/bundle/release/app-release.aab`

## Contributing

When adding new BLE commands:

1. Add method to `TedeeLockService` (Dart)
2. Add handler in `MainActivity` (Kotlin)
3. Add UI button in `LockControlScreen` (Dart)
4. Update this README

## Resources

- **Flutter Documentation:** https://docs.flutter.dev
- **Platform Channels:** https://docs.flutter.dev/platform-integration/platform-channels
- **Tedee BLE API:** https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com
- **Tedee Android SDK:** https://tedee-com.github.io/tedee-mobile-sdk-android/
- **Original Android App:** `../app/` (reference implementation)

## License

Same as parent project.
