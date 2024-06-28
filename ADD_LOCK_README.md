# Adding Tedee Lock to account

## About
This tutorial will guide you through the basic steps required to add a lock to your Tedee account and register the lock device.

## Requirements

### Hardware
1. Tedee Lock - you can order it in our [online store](https://tedee.com/shop/)
   1. Lock must be factory reset. Here is how to do that:

      ![Factory Reset](https://github.com/tedee-com/tedee-example-ble-android/assets/142867104/88446a5c-b5d5-4eac-9730-7b8c7740c348)
      1. Put the lock in a vertical position with the button facing up.
      2. Press and hold the button until the red LED lights up.
      3. Release the button. Three red flashes will confirm the process.

   2. Your lock cannot be added to any account. If you added your lock before, you can delete it from account using Tedee app.
2. Android device (not an emulator) with Android 8.0+ and the [Tedee app](https://play.google.com/store/apps/details?id=tedee.mobile) installed.
3. A PC or Mac capable of running Android Studio.
4. USB cable (to connect the phone to the PC).

### Software
1. [Android Studio](https://developer.android.com/studio)

### Other
1. Tedee Account - Create one in the Tedee app. Download the Tedee app from [Google Play](https://play.google.com/store/apps/details?id=tedee.mobile).

## API Setup
You can create a Personal Access Key (PAK) from portal.tedee.com and use it to authorize at api.tedee.com.
1. Create Personal Access Key.
   1. Log in to the [Tedee Portal](https://portal.tedee.com) with credentials from created Tedee account.
   2. Click on your initials in top right corner.

   ![img2](https://user-images.githubusercontent.com/81370389/209111859-9c022725-1593-4bfd-9d71-72b9e58d4397.png)

   3. Click on Personal Access Keys and generate new access key with at least **Device certificates - Read** scope. Remember to save this key, as it will be needed for the next step.
2. Authorize Tedee API
   1. Go to [Tedee API](https://api.tedee.com) and authorize yourself with created `Personal Access Key` in the previous step.
   2. Click "Authorize" button.

   ![img9](https://user-images.githubusercontent.com/81370389/209112240-01764c21-16c8-4b42-86a6-5ee2374b81d7.png)

   3. Proper format is `PersonalKey [YOUR PERSONAL ACCESS KEY]`.
   4. Confirm with `Authorize`.

   > **Note:** You can also use one-time token by signing in after clicking "Azure B2C Login Page" on top of Swagger website. Paste returned token in the`Bearer [TOKEN]` format

## Adding Device Process

### Step 1 - Get Serial Number Based on Activation Code
Serial number is required to make any connection to the lock. At this point we can get it from Tedee API by providing the activation code.
1. You can find the activation code on the device or on the last page of the instruction manual. An example of an activation code is: `201502XVfSdZze`.
2. Prepare and send the request.
```kotlin
  private var activationCode = "201502XVfSdZze"
  private val mobileService by lazy { MobileService() }

  lifecycleScope.launch {
    val serialNumber = getSerialNumber(activationCode)
  }

  suspend fun getSerialNumber(activationCode: String): String {
    return try {
      mobileService.getSerialNumber(activationCode)
    } catch (error: Exception) {
      throw error
    }
  }
```
> **Note:**  You can check the example implementation of MobileService in the [Example app](./src/main/java/tedee/mobile/ble/example/api/service)

Alternatively, you can use [Tedee API](https://api.tedee.com). Navigate to the Device section and use the `GET /api/v1.32/my/device/getserialnumber` route. Click the `Try it out` button, enter the `ActivationCode` value, and the response will return the Serial Number, which you can use in the next steps.

![getSerialNumberEndpoint](https://github.com/tedee-com/tedee-example-ble-android/assets/142867104/ad7a41a1-82b8-472a-ab9c-8d83eae667c2)
![getSerialNumberParameters](https://github.com/tedee-com/tedee-example-ble-android/assets/142867104/a4fa5bc8-e441-45cc-b5b1-16713e6ed6cd)

> **Note:** Or use the example to get the serial number.
> - Enter the activation code into the activation code edit text (or paste it into the `PRESENT_ACTIVATION_CODE` variable in the [Constants.kt](./src/main/java/tedee/mobile/ble/example/Constants.kt) class and run the app again).
> - Click the `GET SERIAL NUMBER` button to fetch the serial number based on the activation code from the Tedee API. After a successful operation, the serial number edit text will be filled with the new value.
>
>```kotlin
>    const val PERSONAL_ACCESS_KEY: String = "" // Paste here your personal access key
>    const val PRESET_SERIAL_NUMBER = "" // Paste here Lock Serial Number
>    const val PRESET_ACTIVATION_CODE = "" // Paste here Activation Code
>```

### Step 2 - Establish Unsecured Connection with the Lock
Using the serial number, we can establish an unsecured connection to the lock.
1. Ensure the Lock is turned on and within range of your phone. If the Lock is off, press and hold the button for 3 seconds until it starts blinking with red, green, blue, and white lights, then release.
2. Connect to the lock using serial number.
```kotlin
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }
  
  val serialNumber = "20260201-000402"
  addLockConnectionManager.connectForAdding(serialNumber, false, this)
```
> **Note:** Or use example app to establish an unsecured connection:
> Click the `ADD LOCK CONNECT` button to establish unsecure connection with the Lock. After a successful connection, you will see the state information: “Add lock connection established” and three buttons: `ADD LOCK CONNECT`, `SET SIGNED TIME`, and `DISCONNECT`.

### Step 3 - Get Necessary Data from the Lock
Retrieve all necessary data from the lock, required to add it to your account.
1. Set Signed date time
   [setSignedTime](../mobileblesdk/src/main/java/tedee/mobile/sdk/ble/bluetooth/adding/IAddLockInteractor.kt) function from IAddLockInteractor
```kotlin
   suspend fun setSignedTime(signedTime: SignedTime): ByteArray?
```
```kotlin
  private val mobileService by lazy { MobileService() }
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }

  lifecycleScope.launch {
    try {
      val signedTime = getSignedTime()
      addLockConnectionManager.setSignedTime(signedTime)
    } catch (e: Exception) {
      Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
    }
  }

  private suspend fun getSignedTime(): SignedTime {
    return try {
      mobileService.getSignedTime()
    } catch (error: Exception) {
      throw error
    }
  }
```
2. Check Notification
```kotlin
  override fun onNotification(message: ByteArray) {
    if (message.first() == BluetoothConstants.NOTIFICATION_SIGNED_DATETIME) {
      if (message.component2() == BluetoothConstants.API_RESULT_SUCCESS) {
        //Get Lock data and register
      }
    }
  }
```
3. Get Lock data
   The `getLockData'` function is called to get the device settings (getUnsecureDeviceSettings), firmware version (getUnsecureFirmwareVersion), and signature (getSignature). This function returns a CreateDoorLockData object, which contains all the data needed to add the lock to your account.
   [getAddLockData](../mobileblesdk/src/main/java/tedee/mobile/sdk/ble/bluetooth/adding/IAddLockInteractor.kt)
```kotlin
  suspend fun getAddLockData(activationCode: String, serialNumber: String): CreateDoorLockData?
```
```kotlin
  private var activationCode = "201502XVfSdZze"
  private var serialNumber = "12345678-901234"
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }

  lifecycleScope.launch {
    try {
      val createDoorLockData =
        addLockConnectionManager.getAddLockData(activationCode, serialNumber)
      Timber.d("Create Door Lock: $createDoorLockData")
    } catch (e: Exception) {
      Timber.e(e, "GetAddLockData: exception = $e")
    }
  }
```

### Step 4 - Add Lock to Tedee account
Add the lock to your Tedee account via the Tedee API. Use the `CreateDoorLockData` object from the previous step and pass it to the `POST /api/v1.32/my/lock` endpoint.

![Create new lock](https://github.com/tedee-com/tedee-example-ble-android/assets/142867104/3d0bd475-7de5-4ec5-9e11-2c1f20a4c406)

```json
{
  "serialNumber": "20260201-000402",
  "name": "Test1",
  "revision": 1,
  "softwareVersions": [
    {
      "softwareType": 0,
      "version": "2.4.23816"
    }
  ],
  "deviceSettings": {
    "autoLockEnabled": false,
    "autoLockDelay": 270,
    "autoLockImplicitEnabled": false,
    "autoLockImplicitDelay": 5,
    "pullSpringEnabled": false,
    "pullSpringDuration": 60,
    "autoPullSpringEnabled": false,
    "postponedLockEnabled": false,
    "postponedLockDelay": 5,
    "buttonLockEnabled": false,
    "buttonUnlockEnabled": false,
    "hasUnpairedKeypad": null,
    "isCustomPullSpringDuration": false,
    "isCustomPostponedLockDelay": false,
    "isAsync": null,
    "deviceId": -1
  },
  "signature": "ICYCAQAEAmZ5CdgwRAIgHP4d1cGRWKKXosnXmMovPejtmaHlMw8qPZwd2mfN/ysCIGnqnOyFodZ75Z/ugpLU0gpNOMVt5uvPyl4vX/j3ywH8",
  "activationCode": "201502XVfSdZze",
  "organizationId": null,
  "timeZone": "Europe/Warsaw"
}
```
1. Update the lock name. You can name it as you prefer:
```kotlin
createDoorLockData.copy(name = "SDK")
```
2. Prepare and send the request:
```kotlin
  private var activationCode = "201502XVfSdZze"
  private var serialNumber = "12345678-901234"
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }
  private val mobileService by lazy { MobileService() }

  lifecycleScope.launch {
    try {
      val createDoorLockData =
        addLockConnectionManager.getAddLockData(activationCode, serialNumber)
      val updatedDoorLockData = updateCreateDoorLockData(createDoorLockData)
      val newDoorLockResponse = createNewDoorLock(updatedDoorLockData)
    } catch (e: Exception) {
      Timber.e(e, "CreateNewDoorLock: exception = $e")
    }
  }

  private fun updateCreateDoorLockData(createDoorLockData: CreateDoorLockData): CreateDoorLockData {
    return createDoorLockData.copy(name = "SDK")
  }

  private suspend fun createNewDoorLock(createDoorLockResponse: CreateDoorLockData): NewDoorLockResponse {
    return try {
      mobileService.createNewDoorLock(createDoorLockResponse)
    } catch (error: Exception) {
      throw error
    }
  }
```

### Step 5 - Register Lock
Use NewDoorLockResponse from previous step to register the lock:
[registerDevice](../mobileblesdk/src/main/java/tedee/mobile/sdk/ble/bluetooth/adding/IAddLockInteractor.kt)
```kotlin
  suspend fun registerDevice(registerDeviceData: RegisterDeviceData)
```
```kotlin
  val newDoorLockResponse = createNewDoorLock(updatedDoorLockData)
  Timber.d("New Door Lock Response: $newDoorLockResponse")
  val registerDeviceData = 
    RegisterDeviceData(newDoorLockResponse.id, newDoorLockResponse.authPublicKey)
  DEVICE_ID = registerDeviceData.id
  addLockConnectionManager.registerDevice(registerDeviceData)
```

### Step 6 - Lock ready to security connection
The lock is now added to your account and registered. We can proceed to establish a secure connection with the lock.
1. Get Mobile Public Key:
   Call the `getMobilePublicKey()` function from `PublicKeyManager` to obtain the public key for the mobile device. This key is required to obtain the certificate.
2. Get Certificate for the lock:
   Using the `MobilePublicKey` and `DeviceId`, obtain the certificate for the lock. DeviceId you can get from `RegisterDeviceData` object: `registerDeviceData.id`
```kotlin
    private var deviceName = "Lock from SDK"
    private val deviceId = 123456
    private val mobileService by lazy { MobileService() }

    lifecycleScope.launch {
      val mobilePublicKey = getMobilePublicKey().orEmpty()
      try {
        val registerMobileResult = mobileService.registerMobile(
          MobileRegistrationBody(deviceName, publicKey = mobilePublicKey)
        )
        val certificateResult = mobileService.getCertificate(registerMobileResult.id, DEVICE_ID)
        val certificate = certificateResult.certificate
        val devicePublicKey = certificateResult.devicePublicKey
      } catch (e: Exception) {
        Timber.e(e, "GenerateCertificate: exception = $e")
      }
    }
```
3. Now, you can make secured connection with the lock.
```kotlin
  val deviceCertificate = DeviceCertificate(certificate, devicePublicKey)
  lockConnectionManager.connect(SERIAL_NUMBER, deviceCertificate, true, this)
```

> **Note:**  You can check the example implementation of MobileService in the [Example app](./src/main/java/tedee/mobile/ble/example/api/service)
> **Note:** If you encounter any problems, check out our [Example app](./src/main/java/tedee/mobile/ble/example/) or [contact us](https://github.com/tedee-com/tedee-mobile-sdk-android/discussions).

## Summary code
```kotlin
class RegisterLockExampleActivity : AppCompatActivity(), IAddLockConnectionListener {

  private lateinit var binding: ActivityRegisterLockExampleBinding
  private var deviceName = "Lock from SDK"
  private var deviceId: Int = -1
  private var serialNumber = ""
  private var activationCode = PRESET_ACTIVATION_CODE
  private val mobileService by lazy { MobileService() }
  private val addLockConnectionManager by lazy { AddLockConnectionManager(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityRegisterLockExampleBinding.inflate(layoutInflater)
    setContentView(binding.root)
    requestPermissions(getBluetoothPermissions().toTypedArray(), 9)
    lifecycleScope.launch {
      try {
        serialNumber = getSerialNumber(activationCode)
        Toast.makeText(applicationContext, "Serial number: $serialNumber", Toast.LENGTH_SHORT)
          .show()
        addLockConnectionManager.connectForAdding(
          serialNumber,
          false,
          this@RegisterLockExampleActivity
        )
      } catch (e: Exception) {
        showErrorToast(e)
      }
    }
    binding.buttonNavigateToMain.setOnClickListener {
      val intent = Intent(this@RegisterLockExampleActivity, MainActivity::class.java)
      startActivity(intent)
      finish()
    }
  }

  private fun setSignedDateTime() {
    lifecycleScope.launch {
      try {
        val signedTime = getSignedTime()
        addLockConnectionManager.setSignedTime(signedTime)
      } catch (e: Exception) {
        showErrorToast(e)
        return@launch
      }
    }
  }

  private fun registerLock() {
    lifecycleScope.launch {
      try {
        val createDoorLockData =
          addLockConnectionManager.getAddLockData(activationCode, serialNumber)
        val updatedDoorLockData = updateCreateDoorLockData(createDoorLockData)
        val newDoorLockResponse = createNewDoorLock(updatedDoorLockData)
        val registerDeviceData =
          RegisterDeviceData(newDoorLockResponse.id, newDoorLockResponse.authPublicKey)
        deviceId = registerDeviceData.id
        addLockConnectionManager.registerDevice(registerDeviceData)
        Toast.makeText(
          applicationContext,
          "Lock was added: DEVICE_ID: $deviceId, DEVICE_NAME: $deviceName",
          Toast.LENGTH_SHORT
        ).show()
      } catch (e: Exception) {
        showErrorToast(e)
        return@launch
      }
    }
  }

  private suspend fun getSerialNumber(activationCode: String): String {
    return try {
      mobileService.getSerialNumber(activationCode)
    } catch (error: Exception) {
      throw error
    }
  }

  private suspend fun getSignedTime(): SignedTime {
    return try {
      mobileService.getSignedTime()
    } catch (error: Exception) {
      throw error
    }
  }

  private fun updateCreateDoorLockData(createDoorLockData: CreateDoorLockData): CreateDoorLockData {
    return createDoorLockData.copy(name = deviceName)
  }

  private suspend fun createNewDoorLock(createDoorLockResponse: CreateDoorLockData): NewDoorLockResponse {
    return try {
      mobileService.createNewDoorLock(createDoorLockResponse)
    } catch (error: Exception) {
      throw error
    }
  }

  override fun onUnsecureConnectionChanged(isConnecting: Boolean, isConnected: Boolean) {
    when {
      isConnecting -> Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
      isConnected -> {
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
        setSignedDateTime()
      }

      else -> Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onNotification(message: ByteArray) {
    if (message.isEmpty()) return
    Timber.d("LOCK LISTENER: notification: ${message.print()}")
    if (message.first() == NOTIFICATION_SIGNED_DATETIME && message.component2() == API_RESULT_SUCCESS) {
      registerLock()
    }
  }

  override fun onError(throwable: Throwable) {
    Timber.e(throwable, "LOCK LISTENER:: error $throwable")
  }

  private fun showErrorToast(exception: Exception) {
    Toast.makeText(
      applicationContext,
      "${exception::class.java.simpleName} ${exception.message ?: ""}",
      Toast.LENGTH_SHORT
    ).show()
  }

  override fun onDestroy() {
    addLockConnectionManager.clear()
    super.onDestroy()
  }
}
```