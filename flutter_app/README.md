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

### 4. Integration with Existing Android Code (REQUIRED)

⚠️ **CRITICAL STEP:** The certificate generation logic is not yet integrated. You need to copy the following files from the original Android app (`../app/src/main/java/tedee/mobile/demo/`) to the Flutter Android module:

**Files to copy:**
```
From: ../app/src/main/java/tedee/mobile/demo/
To: android/app/src/main/kotlin/com/tedee/flutter/

Copy these files:
├── manager/
│   ├── CertificateManager.kt          # Certificate generation and storage
│   ├── SignedTimeManager.kt           # Time synchronization
│   └── SerialNumberManager.kt         # Serial number retrieval
│
├── datastore/
│   └── DataStoreManager.kt            # Local encrypted storage
│
├── api/
│   ├── service/
│   │   ├── MobileService.kt           # API calls
│   │   ├── MobileApi.kt               # Retrofit interface
│   │   └── ApiProvider.kt             # HTTP client config
│   └── data/model/
│       ├── MobileCertificateResponse.kt
│       ├── RegisterMobileResponse.kt
│       └── MobileRegistrationBody.kt
│
└── SignedTimeProvider.kt              # Time provider implementation
```

**After copying:**
1. Update package names: `tedee.mobile.demo` → `com.tedee.flutter`
2. Update imports in all copied files
3. Integrate `CertificateManager` in `TedeeFlutterBridge.kt`:

```kotlin
// In TedeeFlutterBridge.kt
private val certificateManager = CertificateManager(context, personalAccessKey)

private suspend fun getCertificate(...): DeviceCertificate {
    return certificateManager.registerAndGenerateCertificate(serialNumber, deviceId, name)
}
```

### Alternative: Create a Shared Module (Recommended for Production)

Instead of copying files, create a shared Android library module:

```
tedee-example-ble-android/
├── app/                          # Original Android app
├── flutter_app/                  # Flutter app
└── shared-tedee-sdk/            # NEW: Shared module
    └── src/main/kotlin/
        └── tedee/shared/         # All reusable Tedee SDK logic
```

This avoids code duplication and makes maintenance easier.

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

### Methods (Dart → Kotlin)

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `connect` | serialNumber, deviceId, name, keepConnection | bool | Connect to lock with certificate |
| `disconnect` | - | void | Disconnect from lock |
| `openLock` | - | String | Unlock (BLE command 0x51) |
| `closeLock` | - | String | Lock (BLE command 0x50) |
| `pullSpring` | - | String | Pull spring (BLE command 0x52) |
| `getLockState` | - | String | Get current lock state |

### Callbacks (Kotlin → Dart)

| Callback | Data | Description |
|----------|------|-------------|
| `onNotification` | String | Lock notifications and status updates |

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

### Certificate Error
**Error:** `NotImplementedError: Certificate generation not yet implemented`

**Solution:** Follow step 4 above to integrate the certificate generation code.

### BLE Permissions Error
**Error:** App crashes or can't scan for devices

**Solution:**
1. Grant location permissions manually in device settings
2. Ensure `AndroidManifest.xml` has all required permissions
3. Request permissions at runtime (TODO: add to Flutter code)

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

## Current Limitations

1. **Certificate generation not integrated** - Requires manual integration (see step 4)
2. **No runtime permission requests** - Permissions must be granted manually
3. **Android only** - iOS support requires:
   - Tedee iOS SDK integration
   - Swift/Objective-C MethodChannel implementation
   - iOS-specific UI adjustments
4. **Single lock support** - SDK limitation (same as original app)

## Next Steps

### Immediate
1. ✅ Flutter UI created
2. ✅ MethodChannel structure complete
3. ⚠️ **TODO:** Integrate certificate generation (see step 4)
4. TODO: Add runtime permission requests
5. TODO: Add connection state management
6. TODO: Add error handling UI

### Future Enhancements
1. iOS support with Tedee iOS SDK
2. Multiple lock support (when SDK supports it)
3. Lock registration flow
4. Activity logs retrieval
5. Battery status monitoring
6. Firmware updates

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
