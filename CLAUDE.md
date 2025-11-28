# CLAUDE.md - AI Assistant Guide

This document provides comprehensive guidance for AI assistants working with the Tedee Lock BLE Android example application.

## Project Overview

**Project Name:** Tedee Demo (Tedee Lock Communication Example)
**Language:** Kotlin 2.1.0
**Platform:** Android
**Min SDK:** 26 (Android 8.0+)
**Target SDK:** 35 (Android 15)
**Compile SDK:** 35
**App Version:** 2.0 (versionCode: 2)
**Build System:** Gradle (AGP 8.7.3)
**Java Version:** 17
**Primary Purpose:** Demonstration app showing Bluetooth Low Energy (BLE) communication with Tedee smart locks using the Tedee Lock SDK

### Important Context
- This is a **simplified example** - it omits production-ready error handling and security practices
- Designed for **single lock connection** at a time (SDK limitation)
- Requires **physical Android device** (BLE not supported in emulators)
- Uses **local BLE communication only** (no Tedee cloud services except for initial certificate generation)

## Codebase Structure

```
tedee-example-ble-android/
├── app/src/main/java/tedee/mobile/demo/
│   ├── ExampleApplication.kt              # App initialization (Timber logging)
│   ├── MainActivity.kt                     # Main UI - lock control (291 lines)
│   ├── RegisterLockExampleActivity.kt      # Lock registration workflow (158 lines)
│   ├── SignedTimeProvider.kt               # Implements SDK time provider interface
│   ├── Constants.kt                        # Configuration (PERSONAL_ACCESS_KEY, presets)
│   │
│   ├── api/
│   │   ├── service/
│   │   │   ├── MobileApi.kt                # Retrofit API interface (5 endpoints)
│   │   │   ├── MobileService.kt            # API response processing (80 lines)
│   │   │   └── ApiProvider.kt              # Retrofit & HTTP client config (singleton)
│   │   └── data/model/
│   │       ├── MobileCertificateResponse.kt
│   │       ├── RegisterMobileResponse.kt
│   │       ├── NewDoorLockResponse.kt
│   │       └── MobileRegistrationBody.kt
│   │
│   ├── manager/
│   │   ├── CertificateManager.kt           # Certificate lifecycle management
│   │   ├── SignedTimeManager.kt            # Time synchronization
│   │   ├── CreateDoorLockManager.kt        # Lock creation API wrapper
│   │   └── SerialNumberManager.kt          # Serial number retrieval
│   │
│   ├── datastore/
│   │   └── DataStoreManager.kt             # Secure local storage (singleton)
│   │
│   ├── helper/
│   │   ├── UiSetupHelper.kt                # UI initialization & management (291 lines)
│   │   └── UiHelper.kt                     # UI interface contract
│   │
│   └── adapter/
│       ├── BleResultsAdapter.kt            # RecyclerView for command results
│       └── BleResultItem.kt                # Result message data class
│
├── app/src/main/res/
│   ├── layout/
│   │   ├── activity_main.xml               # Main lock control UI
│   │   ├── activity_register_lock_example.xml
│   │   └── ble_result_item.xml
│   ├── values/
│   │   ├── strings.xml
│   │   ├── colors.xml
│   │   └── themes.xml
│   └── mipmap-*/                           # App icons (adaptive, multiple densities)
│
├── README.md                               # User-facing documentation
├── ADD_LOCK_README.md                      # Lock registration tutorial
└── build.gradle                            # Dependencies and build config
```

## Architecture

### Layered Architecture Pattern

```
┌─────────────────────────────────────┐
│    PRESENTATION LAYER               │
│    MainActivity                     │ ← Implements ILockConnectionListener
│    RegisterLockExampleActivity      │ ← Implements IAddLockConnectionListener
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│    HELPER LAYER                     │
│    UiSetupHelper                    │ ← UI orchestration & state management
│    UiHelper (interface)             │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│    BUSINESS LOGIC LAYER             │
│    CertificateManager               │ ← Certificate lifecycle
│    SignedTimeManager                │ ← Time synchronization
│    CreateDoorLockManager            │ ← Lock creation
│    SerialNumberManager              │ ← Serial lookup
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│    SERVICE LAYER                    │
│    MobileService                    │ ← API calls & response processing
│    Tedee Lock SDK                   │ ← BLE communication
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│    EXTERNAL LAYER                   │
│    MobileApi (Retrofit)             │ ← HTTPS API (api.tedee.com)
│    LockConnectionManager (SDK)      │ ← BLE operations
│    DataStoreManager                 │ ← Local encrypted storage
└─────────────────────────────────────┘
```

## Key Dependencies

```gradle
// Tedee Lock SDK - Core BLE communication
implementation('com.github.tedee-com:tedee-mobile-sdk-android:1.0.0@aar') { transitive = true }

// Networking
implementation "com.squareup.retrofit2:retrofit:2.11.0"
implementation "com.squareup.retrofit2:converter-gson:2.11.0"
implementation "com.squareup.okhttp3:logging-interceptor:4.12.0"

// Storage
implementation "androidx.datastore:datastore-preferences:1.2.0"

// Logging
implementation "com.jakewharton.timber:timber:5.0.1"

// Android Framework
implementation 'androidx.core:core-ktx:1.15.0'
implementation 'androidx.appcompat:appcompat:1.7.1'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
```

**Note:** AndroidX Core 1.15.0 requires `compileSdk 35` or higher.

## Core Data Flows

### 1. Lock Connection Flow (Secure)

```
User Input (Serial Number, Certificate)
    ↓
Validate Certificate (or generate if needed)
    ├─ CertificateManager.registerAndGenerateCertificate()
    ├─ POST /api/v1.32/my/mobile
    ├─ GET /api/v1.32/my/devicecertificate/getformobile
    └─ DataStoreManager.saveCertificateData()
    ↓
MainActivity.setupSecureConnectClickListener()
    ↓
LockConnectionManager.connect(serialNumber, deviceCertificate, keepConnection, listener)
    ↓
ILockConnectionListener callbacks:
    ├─ onLockConnectionChanged(isConnecting, isConnected)
    ├─ onNotification(message: ByteArray)
    ├─ onLockStatusChanged(lockStatus: Int)
    └─ onError(throwable: Throwable)
```

### 2. Lock Registration Flow (Add New Lock)

```
RegisterLockExampleActivity.onCreate()
    ↓
Get Serial Number from Activation Code
    └─ GET /api/v1.32/my/device/getserialnumber?activationCode=X
    ↓
AddLockConnectionManager.connectForAdding(serialNumber, false, listener)
    ↓
Wait for onUnsecureConnectionChanged(isConnected=true)
    ↓
Set Signed Date/Time
    ├─ GET /api/v1.32/datetime/getsignedtime
    └─ AddLockConnectionManager.setSignedTime(signedTime)
    ↓
Wait for NOTIFICATION_SIGNED_DATETIME
    ↓
Register Lock
    ├─ AddLockConnectionManager.getAddLockData(activationCode, serialNumber)
    ├─ POST /api/v1.32/my/Lock (with CreateDoorLockData)
    ├─ Create RegisterDeviceData from response
    └─ AddLockConnectionManager.registerDevice(registerDeviceData)
    ↓
Lock added to account (can now establish secure connection)
```

### 3. Command Execution Flow

```
User clicks control button (Open/Close/Pull Spring)
    ↓
lifecycleScope.launch { ... }
    ↓
LockConnectionManager.openLock(openMode) // or sendCommand(bytes)
    ↓
SDK handles secure BLE transmission
    ↓
ILockConnectionListener.onNotification(message)
    ↓
Parse response and update UI
    └─ UiSetupHelper.addMessage(message)
        └─ BleResultsAdapter displays in RecyclerView
```

## Key Conventions & Patterns

### 1. Listener Pattern for BLE Events

**MainActivity** implements `ILockConnectionListener`:
```kotlin
override fun onLockConnectionChanged(isConnecting: Boolean, isConnected: Boolean)
override fun onNotification(message: ByteArray)
override fun onLockStatusChanged(lockStatus: Int)
override fun onError(throwable: Throwable)
```

**RegisterLockExampleActivity** implements `IAddLockConnectionListener`:
```kotlin
override fun onUnsecureConnectionChanged(isConnecting: Boolean, isConnected: Boolean)
override fun onNotification(message: ByteArray)
override fun onError(throwable: Throwable)
```

### 2. Singleton Pattern

Used for stateless services and providers:
- `ApiProvider` - Single Retrofit instance
- `DataStoreManager` - Single DataStore instance

```kotlin
object DataStoreManager { ... }
object ApiProvider { ... }
```

### 3. Manager Pattern

Business logic encapsulated in dedicated manager classes:
- Each manager has a single responsibility
- Managers coordinate between services and UI
- Use suspend functions for async operations

### 4. Coroutines for Async Operations

Always use `lifecycleScope.launch`:
```kotlin
lifecycleScope.launch {
    try {
        val result = mobileService.getCertificate(mobileId, deviceId)
        // Update UI
    } catch (e: Exception) {
        // Handle error
    }
}
```

Use `withContext(Dispatchers.IO)` for DataStore operations:
```kotlin
suspend fun saveCertificate(cert: String) {
    withContext(Dispatchers.IO) {
        dataStore.edit { preferences ->
            preferences[CERTIFICATE_KEY] = cert
        }
    }
}
```

### 5. View Binding

All activities use view binding (enabled in build.gradle):
```kotlin
private lateinit var binding: ActivityMainBinding

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
}
```

### 6. Timber Logging

Use Timber for all logging (initialized in ExampleApplication):
```kotlin
Timber.d("Debug message")
Timber.e(exception, "Error occurred")
Timber.w("Warning message")
```

### 7. Error Handling Pattern

**API Layer:**
```kotlin
try {
    val response = api.someEndpoint()
    if (response.isSuccessful) {
        return extractResult(response.body())
    } else {
        throw Exception("Error: ${response.errorBody()?.string()}")
    }
} catch (error: Exception) {
    throw error
}
```

**UI Layer:**
```kotlin
lifecycleScope.launch {
    try {
        // Async operation
    } catch (e: Exception) {
        Toast.makeText(this@Activity, e.message, Toast.LENGTH_SHORT).show()
        Timber.e(e, "Operation failed")
    }
}
```

## Development Workflows

### Adding a New Lock Control Command

1. **Check Tedee BLE API Documentation** for command byte code
2. **Add button to activity_main.xml** in the commands section
3. **Add click listener in UiSetupHelper.kt**:
   ```kotlin
   private fun setupNewCommandClickListener() {
       binding.buttonNewCommand.setOnClickListener {
           lifecycleScope.launch {
               try {
                   val result = lockConnectionManager.sendCommand(byteArrayOf(0xXX))
                   addMessage("Command sent: ${result?.print()}")
               } catch (e: Exception) {
                   Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
               }
           }
       }
   }
   ```
4. **Call setup function** from `UiSetupHelper.setup()`
5. **Handle response** in `MainActivity.onNotification()`

### Adding a New API Endpoint

1. **Add endpoint to MobileApi.kt**:
   ```kotlin
   @GET("api/v1.32/my/newEndpoint")
   suspend fun getNewData(@Query("param") param: String): Response<JsonObject>
   ```

2. **Add service method in MobileService.kt**:
   ```kotlin
   suspend fun getNewData(param: String): NewDataResponse {
       val response = ApiProvider.api.getNewData(param)
       if (response.isSuccessful) {
           val result = response.body()?.getAsJsonObject("result")
           return gson.fromJson(result, NewDataResponse::class.java)
       } else {
           throw Exception("Error: ${response.errorBody()?.string()}")
       }
   }
   ```

3. **Create data model** in `api/data/model/`:
   ```kotlin
   data class NewDataResponse(
       val field1: String,
       val field2: Int
   )
   ```

4. **Use in activity or manager**:
   ```kotlin
   lifecycleScope.launch {
       try {
           val data = mobileService.getNewData("value")
           // Use data
       } catch (e: Exception) {
           Timber.e(e, "Failed to fetch data")
       }
   }
   ```

### Storing New Configuration Data

1. **Add key to DataStoreManager.kt**:
   ```kotlin
   private val NEW_DATA_KEY = stringPreferencesKey("new_data")

   suspend fun saveNewData(value: String) {
       withContext(Dispatchers.IO) {
           dataStore.edit { it[NEW_DATA_KEY] = value }
       }
   }

   suspend fun getNewData(): String? {
       return withContext(Dispatchers.IO) {
           dataStore.data.first()[NEW_DATA_KEY]
       }
   }
   ```

2. **Use in code**:
   ```kotlin
   lifecycleScope.launch {
       DataStoreManager.saveNewData("value")
       val value = DataStoreManager.getNewData()
   }
   ```

## Configuration Requirements

### Before Running the App

Users must configure in `Constants.kt`:
```kotlin
object Constants {
    const val PERSONAL_ACCESS_KEY: String = "" // Required from portal.tedee.com

    // Optional presets (auto-populate UI fields)
    const val PRESET_SERIAL_NUMBER = ""
    const val PRESET_DEVICE_ID = ""
    const val PRESET_NAME = ""
    const val PRESET_ACTIVATION_CODE = ""
}
```

### Getting Personal Access Key

1. Log in to https://portal.tedee.com
2. Click on initials (top right)
3. Navigate to "Personal Access Keys"
4. Generate key with **Device certificates - Read** scope minimum
5. Paste into `Constants.PERSONAL_ACCESS_KEY`

### Lock Information Sources (from Tedee App)

- **Serial Number**: Lock > Settings > Information > Serial number
- **Device ID**: Lock > Settings > Information > Device ID
- **Lock Name**: Lock > Settings > Lock name
- **Activation Code**: Physical device or instruction manual

## Permission Handling

### Required Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### Runtime Permission Request

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
}
```

**Note:** Location permissions are required by Android for BLE scanning (not for actual GPS).

## Important Files Reference

### Entry Points
- `MainActivity.kt:291` - Main lock control interface
- `RegisterLockExampleActivity.kt:158` - Lock registration workflow
- `ExampleApplication.kt` - App initialization

### Core Business Logic
- `CertificateManager.kt:68` - Certificate generation and storage
- `UiSetupHelper.kt:291` - All UI initialization and event handling
- `MobileService.kt:80` - API communication layer

### Configuration
- `Constants.kt` - All configuration constants
- `app/build.gradle` - Dependencies and SDK versions
- `AndroidManifest.xml` - Permissions and activity declarations

### Resources
- `layout/activity_main.xml` - Main UI layout
- `strings.xml` - UI strings and spinner options
- `colors.xml` - Theme colors (includes midnight_blue #22345a)

## Testing Considerations

### Current State
- Unit tests: `src/test/` - Not populated (example only)
- Instrumented tests: `src/androidTest/` - Not populated (example only)

### Recommended Testing Approach
1. **BLE Mocking**: Use mock implementations of `LockConnectionManager`
2. **API Mocking**: Use MockWebServer for Retrofit testing
3. **UI Testing**: Espresso for activity interactions
4. **Unit Testing**: Test managers and data models in isolation

### Testing Constraints
- BLE cannot be tested in emulator
- Requires physical lock hardware for integration testing
- API requires valid Personal Access Key

## Common Pitfalls & Important Notes

### 1. Certificate Expiration
Certificates have expiration dates. If connection fails:
- Check certificate expiration in response
- Regenerate certificate if expired
- Certificates are deleted on app uninstall

### 2. Single Lock Limitation
SDK supports **only one lock connection at a time**:
- Must disconnect before connecting to another lock
- Always call `lockConnectionManager.clear()` in `onDestroy()`

### 3. BLE Requires Physical Device
- Android emulators don't support BLE
- Must test on physical Android device with BLE capability
- USB debugging must be enabled

### 4. RxJava Error Handler
MainActivity sets up error handler for undelivered BLE exceptions:
```kotlin
RxJavaPlugins.setErrorHandler { throwable ->
    if (throwable is UndeliverableException && throwable.cause is BleException) {
        return@setErrorHandler
    }
    throw throwable
}
```

### 5. Keep Connection Parameter
`LockConnectionManager.connect()` has `keepConnection` parameter:
- `true`: Maintains indefinite connection
- `false`: Limited time connection (default)
- Controlled by switch in UI

### 6. API Authentication Format
```kotlin
Authorization: PersonalKey [YOUR_PERSONAL_ACCESS_KEY]
```
Not `Bearer`, use `PersonalKey` prefix.

### 7. Notification Parsing
BLE notifications are ByteArray. Use SDK extension functions:
```kotlin
override fun onNotification(message: ByteArray) {
    Timber.d("NOTIFICATION: ${message.print()}") // Hex string
    if (message.first() == BluetoothConstants.NOTIFICATION_SIGNED_DATETIME) {
        if (message.component2() == BluetoothConstants.API_RESULT_SUCCESS) {
            // Success
        }
    }
}
```

### 8. Lifecycle Management
Always clean up in `onDestroy()`:
```kotlin
override fun onDestroy() {
    lockConnectionManager.clear()
    super.onDestroy()
}
```

### 9. Kotlin 2.1.0 Type Safety
Kotlin 2.1.0 has stricter type checking for byte literals. When using BLE commands:

**Correct** - Use `.toByte()` for hex literals:
```kotlin
lockConnectionManager.sendCommand(0x51.toByte())  // ✓ Correct
```

**Incorrect** - Will cause type mismatch error:
```kotlin
lockConnectionManager.sendCommand(byteArrayOf(0x51))  // ✗ Wrong - expects Byte, not ByteArray
lockConnectionManager.sendCommand(0x51)               // ✗ Wrong - expects Byte, not Int
```

**Important for Cylinder Commands:**
- Open (unlock): `sendCommand(0x51.toByte())`
- Close (lock): `sendCommand(0x50.toByte())`
- Pull spring: `sendCommand(0x52.toByte())`

Note: SDK helper methods like `openLock()` and `closeLock()` may not work with cylinders. Use direct BLE commands instead.

## API Reference

### Tedee API Base URL
`https://api.tedee.com/`

### Endpoints Used (API v1.32)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1.32/my/mobile` | Register mobile device |
| GET | `/api/v1.32/my/devicecertificate/getformobile` | Get certificate for lock |
| GET | `/api/v1.32/datetime/getsignedtime` | Get synchronized time |
| GET | `/api/v1.32/my/device/getserialnumber` | Get serial from activation code |
| POST | `/api/v1.32/my/Lock` | Add lock to account |

### Response Format
All responses follow this structure:
```json
{
    "result": { /* actual data */ },
    "success": true,
    "errorMessages": []
}
```

MobileService extracts the `result` object.

## Tedee Lock SDK Reference

### Main Classes from SDK

**LockConnectionManager** - Secure connection
```kotlin
fun connect(serialNumber: String, deviceCertificate: DeviceCertificate,
            keepConnection: Boolean, listener: ILockConnectionListener)
fun disconnect()
fun sendCommand(command: ByteArray): ByteArray?
fun openLock(openMode: Int = 0): ByteArray?
fun closeLock(closeMode: Int = 0): ByteArray?
fun pullSpring(): ByteArray?
fun getUnsecureDeviceSettings(): ByteArray?
fun getUnsecureFirmwareVersion(): ByteArray?
fun clear()
```

**AddLockConnectionManager** - Registration
```kotlin
fun connectForAdding(serialNumber: String, keepConnection: Boolean,
                     listener: IAddLockConnectionListener)
suspend fun setSignedTime(signedTime: SignedTime): ByteArray?
suspend fun getAddLockData(activationCode: String, serialNumber: String): CreateDoorLockData?
suspend fun registerDevice(registerDeviceData: RegisterDeviceData)
fun clear()
```

**Interfaces to Implement**
- `ILockConnectionListener` - For secure connections
- `IAddLockConnectionListener` - For registration
- `ISignedTimeProvider` - Provides current signed time

## Build & Deployment

### Build Configuration
```gradle
android {
    compileSdk 35
    minSdk 26
    targetSdk 35

    defaultConfig {
        versionCode 2
        versionName "2.0"
    }

    buildFeatures {
        viewBinding true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}
```

**Important:**
- Java 17 is required for modern Android development (AGP 8.7.3, Kotlin 2.1.0)
- `compileSdk 35` is required by AndroidX Core 1.15.0
- `jvmTarget` must match Java version for Kotlin compatibility

### Build Variants
- **Debug**: Full debug info, debug signing
- **Release**: No minification (minifyEnabled false), debug signing

### Building the App
```bash
# From Android Studio: Run > Run 'app' or Shift+F10
# From command line:
./gradlew assembleDebug
./gradlew assembleRelease
```

## Useful Resources

- **Tedee Lock SDK Documentation**: https://tedee-com.github.io/tedee-mobile-sdk-android/
- **Tedee Lock BLE API Documentation**: https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/
- **Tedee API Swagger**: https://api.tedee.com
- **Tedee Portal**: https://portal.tedee.com
- **Main README**: `README.md` - Complete setup instructions
- **Lock Registration Tutorial**: `ADD_LOCK_README.md` - Step-by-step guide

## Quick Command Reference

### BLE Command Codes - Complete List

All commands require a PTLS (secure) session unless noted otherwise.

#### **Operations Commands**
- `0x50` - **LOCK** - Lock the lock
  - Parameters: `0x00` = None, `0x02` = Force (emergency)
- `0x51` - **UNLOCK** - Unlock the lock
  - Parameters: `0x00` = None, `0x01` = Auto, `0x02` = Force (emergency)
- `0x52` - **PULL_SPRING** - Pull the spring latch
  - Parameters: None

#### **State & Information Commands**
- `0x5A` - **GET_STATE** - Get current lock state and jam status
  - Returns: Lock state (unlocked/locked/pulling/etc.) + jam status
- `0x0C` - **GET_BATTERY** - Get battery level and charging status
  - Returns: Battery % (0-100) + charging status (0=discharging, 1=charging)

#### **Calibration Commands**
- `0x53` - **CALIBRATION_INIT** - Initialize calibration process
- `0x54` - **CALIBRATE_LOCKED** - Calibrate locked position
- `0x55` - **CALIBRATE_UNLOCKED** - Calibrate unlocked position
- `0x56` - **CALIBRATION_CANCEL** - Cancel calibration

#### **Pull Spring Calibration Commands**
- `0x57` - **PULL_CALIBRATION_INIT** - Initialize pull spring calibration
- `0x58` - **PULL_CALIBRATION_START** - Start pull spring calibration
- `0x59` - **PULL_CALIBRATION_CANCEL** - Cancel pull spring calibration

#### **Activity Logs Commands**
- `0x2D` - **GET_LOGS_TLV** - Retrieve activity logs in TLV format
  - Result codes:
    - `0x00` = SUCCESS (more logs available)
    - `0x04` = NOT_FOUND (last/empty package)
    - `0x03` = BUSY (retry in 100-500ms)
    - `0x02` = ERROR (MTU too small)
    - `0x07` = NO_PERMISSION

#### **Security Commands**
- `0x71` - **SET_SIGNED_DATETIME** - Set trusted date/time from API

### BLE Notifications (from Lock to App)

Notifications are sent by the lock to inform about state changes and events:

- `0xA5` - **HAS_LOGS** - Activity logs are ready to be collected
  - Triggered after connection, indicates logs waiting to download
- `0xBA` - **LOCK_STATUS_CHANGE** - Lock state has changed
  - Contains current lock state and operation result

### Lock States (in notifications)
- `0x00` - **UNCALIBRATED** - Lock not calibrated
- `0x01` - **CALIBRATION** - Calibration in progress
- `0x02` - **UNLOCKED** - Lock is unlocked (opened)
- `0x03` - **PARTIALLY_UNLOCKED** - Partially unlocked position
- `0x04` - **UNLOCKING** - Unlock operation in progress
- `0x05` - **LOCKING** - Lock operation in progress
- `0x06` - **LOCKED** - Lock is locked (closed)
- `0x07` - **PULL_SPRING** - Pull spring position
- `0x08` - **PULLING** - Pull spring operation in progress
- `0x09` - **UNKNOWN** - Unknown state

### Common Result Codes
- `0x00` - **SUCCESS** - Operation accepted/successful
- `0x01` - **INVALID_PARAM** - Invalid parameters
- `0x02` - **ERROR** - Operation error
- `0x03` - **BUSY** - Lock performing other operations
- `0x04` - **NOT_FOUND** - Resource not found
- `0x05` - **NOT_CALIBRATED** - Lock needs calibration
- `0x07` - **NO_PERMISSION** - Insufficient permissions
- `0x08` - **NOT_CONFIGURED** - Feature not configured
- `0x09` - **DISMOUNTED** - Lock not mounted on door

### Important Notes

**Cylinder vs Standard Lock:**
- Tedee **cylinders** use the codes listed above (confirmed working)
- Standard Tedee **locks** may use different command codes for some operations
- The app uses direct BLE commands (`sendCommand()`) for cylinder compatibility

**SDK Methods:**
- `getDeviceSettings()` and `getFirmwareVersion()` are SDK helper methods
- These are NOT direct BLE commands but may use internal/undocumented codes
- They may only work during unsecured connection (device registration)

## Code Style Guidelines

### Kotlin Conventions
- Use `val` over `var` when possible
- Use data classes for models
- Use sealed classes for state representation
- Prefer coroutines over callbacks

### Naming Conventions
- Activities: `*Activity.kt`
- Managers: `*Manager.kt`
- Data models: Descriptive nouns (e.g., `MobileCertificateResponse`)
- Interfaces: `I*` prefix (SDK convention)
- Constants: UPPER_SNAKE_CASE

### Organization
- Group related functionality in packages
- Keep activities focused on UI logic
- Extract business logic to managers
- Use helpers for complex UI setup

## Summary for AI Assistants

When working with this codebase:

1. **Always check README.md and ADD_LOCK_README.md** for user-facing documentation
2. **Respect the layered architecture** - don't bypass layers
3. **Use coroutines** for all async operations (lifecycleScope.launch)
4. **Handle errors gracefully** with try-catch and user-friendly messages
5. **Follow the listener pattern** for BLE events
6. **Remember single lock limitation** - SDK supports one connection at a time
7. **Use Timber for logging**, not println or Log
8. **Test on physical devices** - emulator doesn't support BLE
9. **Check certificate expiration** when connection issues occur
10. **Reference Tedee BLE API docs** for command bytes and responses

This is an example/demo app - prioritize clarity and simplicity over production-grade complexity.
