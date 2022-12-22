# tedee lock communication example

![lock](https://user-images.githubusercontent.com/81370389/209109383-c9163001-cc5b-418b-be65-87906a3cc11c.jpg)

## About

This example project was created by the [tedee](https://tedee.com) team to show you how to operate the tedee lock using Bluetooth Low Energy communication protocol.

This project was created using Kotlin language and it runs on Android devices.

The purpose of this project is to present how you can establish a Bluetooth connection with tedee Lock, start an encrypted session and operate it (currently only the `Unlock` command is implemented). 

App uses RxAndroidBle library and custom implementation of secure BLE session. It is not using any tedee services and works only locally within the range of BLE. During the preparation steps, you will have to get the lock certificate manually.

With this example, you will be able to operate only one lock at a time.

> :warning: This is just a simplified example of how to connect and send commands to lock. It omits security concerns, error handling, and best practices. This is not production-ready code.

## Requirements

### Hardware
1. Tedee Lock - you can order it in our [online store](https://tedee.com/shop/)
2. Android (not emulator) with Android 8.0+ and installed [Tedee](https://play.google.com/store/apps/details?id=tedee.mobile) app
3. Any PC/Mac able to run Android Studio
4. USB cable (to connect the phone to PC)

### Software
1.  [Android Studio](https://developer.android.com/studio)

### Other
1. Tedee account created in tedee app
2. Lock added and calibrated in tedee app

## Initial configuration

### Step 1 - configure project
1. Clone this repository, unpack the archive
2. Select File > Open in Android Studio and click on the downloaded folder
3. The project will be imported and configured automatically. Android Studio will prompt you with any needed updates.

### Step 2 - Pairing Phone with Android Studio

1. Enable `Developer Options` on your phone [How to enable](https://developer.android.com/studio/debug/dev-options)
2. Enable `USB debugging` in `Developer Options`
3. Open Android Studio, connect the phone via USB
4. Your phone may display a popup asking about trusting PC, approve the connection.
5. Now you should see your Android device in top bar 

  ![img11](https://user-images.githubusercontent.com/81370389/209111218-89542388-71e3-4379-80aa-2138baec4424.png)

> :warning: USB debugging allows to control your phone from any connected computer. Make sure to disable it after finishing working on the project.

### Step 3 - Running project

**Now you are ready to build the project and upload it to your device.**

Click on "Run 'app'" button or use `Shift + F10` to build project and run it on connected device. 

#### First Launch

On first launch app will ask you for permission to use Location and Bluetooth. Both are needed for Android app to access Bluetooth module.

The app will also generate public key that is required to generate a lock certificate (see next steps). Look for `!!! Public key to register mobile:` in Logcat (bottom of Android Studio). Save the line below it for the next step. Pay attention to the Logcat, as you will see there also steps that are taken by the app to unlock the lock (connect, start an encrypted session, send unlock command, receive a response).
### Step 4 - register tedee example app
1. Log in to [tedee Portal](https://portal.tedee.com) with credentials from created tedee account 
2. Click on your initials in top right corner 

![img2](https://user-images.githubusercontent.com/81370389/209111859-9c022725-1593-4bfd-9d71-72b9e58d4397.png)

3. Click on Personal Access Keys and generate new access key with at least **Device certificates - Read** scope
4. Go to [tedee API](https://api.tedee.com) and authorize yourself with created Personal Access Key
5. Click "Authorize" button

![img9](https://user-images.githubusercontent.com/81370389/209112240-01764c21-16c8-4b42-86a6-5ee2374b81d7.png)

6. Proper format is `PersonalKey [YOUR PERSONAL ACCESS KEY]`
7. Confirm with `Authorize`
8. Go to `Mobile` section and use `POST /api/[api version]/my/mobile` route and click on `Try it out` button

![img13](https://user-images.githubusercontent.com/81370389/209114544-3764f0f9-0a03-41a7-bb67-426e1514f154.png)

9. Enter `name` (lock name from tedee app), `operatingSystem` set to `3` and `publicKey` the one that was copied in "First launch" step
10. Response will return `id` that is required in next step (as `MobileID`)
11. Go to `DeviceCertificate` section and use `/api/[api version]/my/devicecertificate/getformobile`

![img10](https://user-images.githubusercontent.com/81370389/209114588-68facc13-b162-48f7-baaa-6dd605b2228a.png)

12. Click on `Try it out`
13. Fill `MobileID` gathered from previous request response
14. Fill `DeviceId` gathered from tedee app (click: Lock > Settings > Information > Device ID)
15. Click `Execute`, store somewhere response result, you will need it in next step

> :warning: Generated certificate has expiration date, which is attached to the response with certificate. After certificate expiration you will not be able to operate the lock and you need to get new one.

### Step 5 - add device certificate and serial number to project

1. Open MainActivity.kt in project navigator
2. Replace value of `LOCK_SERIAL` with your tedee lock serial number from tedee app (click: Lock > Settings > Information > Serial number)
3. Replace value of `CERTIFICATE` with `result.certificate` of API request 
4. Replace value of `DEVICE_PUBLIC_KEY` with `result.devicePublicKey` of API request 

### Step 6 - operate the lock

1. Make sure your lock was calibrated with tedee app and is in locked state 
2. Compile and run app again with `Run 'app'` button or use `Shift + F10`
3. Click "Connect" button
4. After app connects to lock, click "Unlock" button
5. App should unlock the lock, see Logcat. `MESSAGE:` tag indicates BT message sent by mobile, `LOCK:` tag indicates BT response from lock. 51 is command id for unlock, response contains additional parameter. If the parameter is 00, the lock sucessfully started the operation.

![img15](https://user-images.githubusercontent.com/81370389/209112722-46611d90-0556-4725-8e9e-9b80aae2531c.png)

> :warning: If you are unable to connect to lock, please check if your mobile certificate did not change. It is deleted after every app uninstall. If it changes, repeat step 4
