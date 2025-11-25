# Integration Guide: Flutter + Android Native Code

This guide explains how to integrate the existing Android Tedee SDK code into the Flutter app.

## Current Status

✅ **Completed:**
- Flutter UI with lock control interface
- MethodChannel setup (Dart ↔ Kotlin communication)
- Android build configuration with Tedee SDK dependencies
- TedeeFlutterBridge structure

⚠️ **TODO:**
- Copy certificate generation logic from existing Android app
- Test end-to-end functionality

## Quick Integration Steps

### Option 1: Copy Individual Files (Quick Start)

1. **Copy manager classes:**
```bash
cd /home/user/tedee-example-ble-android

# Create directories
mkdir -p flutter_app/android/app/src/main/kotlin/com/tedee/flutter/manager
mkdir -p flutter_app/android/app/src/main/kotlin/com/tedee/flutter/datastore
mkdir -p flutter_app/android/app/src/main/kotlin/com/tedee/flutter/api/service
mkdir -p flutter_app/android/app/src/main/kotlin/com/tedee/flutter/api/data/model

# Copy files
cp app/src/main/java/tedee/mobile/demo/manager/*.kt \
   flutter_app/android/app/src/main/kotlin/com/tedee/flutter/manager/

cp app/src/main/java/tedee/mobile/demo/datastore/*.kt \
   flutter_app/android/app/src/main/kotlin/com/tedee/flutter/datastore/

cp app/src/main/java/tedee/mobile/demo/api/service/*.kt \
   flutter_app/android/app/src/main/kotlin/com/tedee/flutter/api/service/

cp app/src/main/java/tedee/mobile/demo/api/data/model/*.kt \
   flutter_app/android/app/src/main/kotlin/com/tedee/flutter/api/data/model/

cp app/src/main/java/tedee/mobile/demo/SignedTimeProvider.kt \
   flutter_app/android/app/src/main/kotlin/com/tedee/flutter/
```

2. **Update package names in all copied files:**
```bash
cd flutter_app/android/app/src/main/kotlin/com/tedee/flutter

# Find and replace package names
find . -name "*.kt" -type f -exec sed -i 's/package tedee\.mobile\.demo/package com.tedee.flutter/g' {} \;
find . -name "*.kt" -type f -exec sed -i 's/import tedee\.mobile\.demo/import com.tedee.flutter/g' {} \;
```

3. **Update TedeeFlutterBridge.kt:**

Replace the `getCertificate()` function with:

```kotlin
import com.tedee.flutter.manager.CertificateManager
import com.tedee.flutter.datastore.DataStoreManager

class TedeeFlutterBridge(
    private val context: Context,
    private val lockConnectionManager: LockConnectionManager
) {
    private val personalAccessKey = "snwu6R.eC+Xuad0sx5inRRo0AaZkYe+EURqYpWwrDR3lU5kuNc="
    private val certificateManager = CertificateManager(context, personalAccessKey)

    private suspend fun getCertificate(
        serialNumber: String,
        deviceId: String,
        name: String
    ): DeviceCertificate {
        return certificateManager.registerAndGenerateCertificate(
            serialNumber = serialNumber,
            deviceId = deviceId,
            name = name
        )
    }

    // Rest of the class...
}
```

4. **Update MainActivity.kt:**

Replace the `connectToLock()` function:

```kotlin
import com.tedee.flutter.TedeeFlutterBridge

class MainActivity : FlutterActivity(), ILockConnectionListener {
    private lateinit var tedeeFlutterBridge: TedeeFlutterBridge

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        tedeeFlutterBridge = TedeeFlutterBridge(this, lockConnectionManager)

        // In the MethodChannel handler:
        when (call.method) {
            "connect" -> {
                // Extract args...
                scope.launch {
                    try {
                        tedeeFlutterBridge.connect(
                            serialNumber, deviceId, name, keepConnection,
                            this@MainActivity
                        )
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("CONNECT_FAILED", e.message, null)
                    }
                }
            }
            // ... rest of handlers
        }
    }
}
```

### Option 2: Shared Module (Recommended for Production)

Create a shared Gradle module that both apps can depend on:

```
tedee-example-ble-android/
├── app/                    # Original Android app
├── flutter_app/            # Flutter app
└── shared-tedee/          # NEW: Shared module
    ├── build.gradle
    └── src/main/kotlin/tedee/shared/
        ├── manager/
        ├── datastore/
        └── api/
```

**Benefits:**
- No code duplication
- Single source of truth
- Easier maintenance
- Shared updates automatically

**Setup:**
1. Create `shared-tedee/` module
2. Move all reusable code there
3. Add dependency in both apps:
   ```gradle
   dependencies {
       implementation project(':shared-tedee')
   }
   ```

## Testing After Integration

1. **Install Flutter SDK:**
```bash
# Follow: https://docs.flutter.dev/get-started/install
flutter doctor
```

2. **Setup local.properties:**
```bash
cd flutter_app/android
cp local.properties.example local.properties
# Edit with your paths
```

3. **Get Flutter dependencies:**
```bash
cd flutter_app
flutter pub get
```

4. **Run on device:**
```bash
flutter run
```

5. **Test lock control:**
- Tap "Connect" button
- Should see "✅ Connected" status
- Try "Open", "Close", "Pull Spring" buttons
- Check message log for responses

## Troubleshooting

### Package Name Errors
If you see errors like "Unresolved reference: CertificateManager":
- Check package names in all copied files
- Verify imports are updated
- Rebuild project: `flutter clean && flutter run`

### Certificate API Errors
If connection fails with API errors:
- Verify `personalAccessKey` is correct
- Check internet connection (for certificate generation)
- Review logs: `adb logcat | grep Tedee`

### Build Errors
```bash
cd flutter_app/android
./gradlew clean
./gradlew assembleDebug
```

## Architecture After Integration

```
┌─────────────────────────────────────┐
│        FLUTTER (Dart)               │
│  - lock_control_screen.dart         │
│  - tedee_lock_service.dart          │
└──────────────┬──────────────────────┘
               │ MethodChannel
┌──────────────▼──────────────────────┐
│    ANDROID NATIVE (Kotlin)          │
│  - MainActivity.kt                  │
│  - TedeeFlutterBridge.kt           │
│    ├─> CertificateManager          │
│    ├─> DataStoreManager            │
│    ├─> MobileService                │
│    └─> LockConnectionManager (SDK) │
└─────────────────────────────────────┘
```

## Key Files Reference

| File | Purpose |
|------|---------|
| `lib/main.dart` | Flutter app entry point |
| `lib/services/tedee_lock_service.dart` | MethodChannel wrapper (Dart) |
| `lib/screens/lock_control_screen.dart` | Lock control UI |
| `android/.../MainActivity.kt` | MethodChannel handler (Kotlin) |
| `android/.../TedeeFlutterBridge.kt` | Bridge to Tedee SDK |
| `android/.../manager/CertificateManager.kt` | Certificate logic (from original app) |
| `android/.../datastore/DataStoreManager.kt` | Local storage (from original app) |
| `android/.../api/service/MobileService.kt` | API calls (from original app) |

## Next Steps After Integration

1. Test all lock commands (open, close, pull spring)
2. Add error handling UI
3. Add loading indicators
4. Implement runtime permission requests
5. Add lock registration flow (add new lock)
6. Consider iOS support with Tedee iOS SDK

## Questions?

- Check main `README.md` for general setup
- Check `CLAUDE.md` in parent directory for architecture reference
- Review original Android app in `../app/` for implementation details
