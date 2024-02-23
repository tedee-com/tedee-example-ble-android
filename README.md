# Tedee Lock communication example

![lock](https://user-images.githubusercontent.com/81370389/209109383-c9163001-cc5b-418b-be65-87906a3cc11c.jpg)

## About

This example project was created by the [Tedee](https://tedee.com) team to show you how to operate the Tedee Lock using Bluetooth Low Energy communication protocol.
This project is developed using the Kotlin language and is designed to run on Android devices. It utilizes our Tedee Lock SDK, which can be accessed via the following link: [LINK TO SDK]().
Navigate to the `build.gradle` file located in the Module: app directory. Within this file, locate the dependencies section where you'll find the dependency for the Tedee Lock SDK.

The purpose of this project is to demonstrate how you can integrate the Tedee Lock SDK into your own app. It provides you with the capability to:
- Establish a Bluetooth connection with a Tedee Lock by providing necessary data such as the Lock's Serial Number, Certificate, and Mobile Public Key.
- Disconnect the connection from the lock.
- Send commands to the lock based on the BLE documentation.
- Receive callbacks when the connection status changes.
- Receive callbacks when indication messages are received.
- Receive callbacks when notification messages are received.
- Handle errors that occur during lock connection.

App uses RxAndroidBle library and custom implementation of secure BLE session (All from Tedee Lock SDK). It is not using any Tedee services and works only locally within the range of BLE. During the preparation steps, you will have to get the lock certificate manually.

With the SDK, you will be able to operate only one lock at a time.

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
1. Tedee account created in Tedee app
2. Lock added and calibrated in Tedee app

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

### Step 4 - Register Tedee example app
1. Log in to [Tedee Portal](https://portal.tedee.com) with credentials from created Tedee account 
2. Click on your initials in top right corner 

![img2](https://user-images.githubusercontent.com/81370389/209111859-9c022725-1593-4bfd-9d71-72b9e58d4397.png)

3. Click on Personal Access Keys and generate new access key with at least **Device certificates - Read** scope
4. Go to [Tedee API](https://api.tedee.com) and authorize yourself with created Personal Access Key
5. Click "Authorize" button

![img9](https://user-images.githubusercontent.com/81370389/209112240-01764c21-16c8-4b42-86a6-5ee2374b81d7.png)

6. Proper format is `PersonalKey [YOUR PERSONAL ACCESS KEY]`
7. Confirm with `Authorize`

> :information_source: You can also use one-time token by signing in after clicking "Azure B2C Login Page" on top of Swagger website. Paste returned token similarly as in step 6. in `Bearer [TOKEN]` format

8. Go to `Mobile` section and use `POST /api/[api version]/my/mobile` route and click on `Try it out` button

![img13](https://user-images.githubusercontent.com/81370389/209114544-3764f0f9-0a03-41a7-bb67-426e1514f154.png)

9. Enter `name` (lock name from Tedee app), `operatingSystem` set to `3` and `publicKey` the one that was copied in "First launch" step
10. Response will return `id` that is required in next step (as `MobileID`)
11. Go to `DeviceCertificate` section and use `/api/[api version]/my/devicecertificate/getformobile`

![img10](https://user-images.githubusercontent.com/81370389/209114588-68facc13-b162-48f7-baaa-6dd605b2228a.png)

12. Click on `Try it out`
13. Fill `MobileID` gathered from previous request response
14. Fill `DeviceId` gathered from Tedee app (click: Lock > Settings > Information > Device ID)
15. Click `Execute`, store somewhere response result, you will need it in next step

> :warning: Generated certificate has expiration date, which is attached to the response with certificate. After certificate expiration you will not be able to operate the lock and you need to get new one.

### Step 5 - Add device certificate and serial number to project

1. Open MainActivity.kt in project navigator
2. Replace value of `LOCK_SERIAL` with your Tedee lock serial number from Tedee app (click: Lock > Settings > Information > Serial number)
3. Replace value of `CERTIFICATE` with `result.certificate` of API request 
4. Replace value of `DEVICE_PUBLIC_KEY` with `result.devicePublicKey` of API request_


### Step 6 - Choose whether to keep the connection with the lock or not.

> :warning: By default, the connection method from the Tedee Lock SDK has the keepConnection parameter set to false. However, in our example app, we set it to true:

```
lockConnectionManager.connect(
    serialNumber = LOCK_SERIAL,
    deviceCertificate = DeviceCertificate(CERTIFICATE, DEVICE_PUBLIC_KEY),
    keepConnection = true,
    listener = this
)
```

When `keepConnection` is set to true, the app will attempt to maintain the connection for a longer period compared to when it is set to false.

1. To change the `keepConnection` parameter, open MainActivity.kt in the project navigator.
2. Find the`connectLock` method and change value for `keepConnection`.


### Step 7 - Send a command to the lock

1. Compile and run app again with `Run 'app'` button or use `Shift + F10`.
2. Click the `Connect` button.
3. Once the app is connected to the lock, you'll see two EditText fields and a `SEND` button. In the first EditText, you can input the `LOCK COMMAND` (e.g., 0x51 to open the lock). The second EditText is optional and allows you to input parameters to be sent with the command (e.g., 0x02 to force unlock). You can input multiple values in the params field by separating them with spaces (e.g., 0x02 0x04). For BLE commands documentation, refer to [Tedee Lock BLE API Documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html) and navigate to the `COMMANDS` section from the right menu.
4. The app should execute the command, and you can observe the Logcat. Messages tagged with `MESSAGE:` indicate BT messages sent by the mobile device, while messages tagged with `LOCK LISTENER message:` indicate BT responses from the lock. For instance, the unlock command has a value of 51. The response may contain additional parameters. If the parameter is 00, the lock successfully initiated the operation. For more information, refer to [Tedee Lock BLE API Documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html).

![img15](https://user-images.githubusercontent.com/81370389/209112722-46611d90-0556-4725-8e9e-9b80aae2531c.png)

5. The app listens for notifications from the lock. Check the Logcat for notifications tagged with `NOTIFICATION:`. For example, after sending the unlock command (0x51), the app listens for notifications and receives lock state changes if the operation is successful. The first state is opening state (0x04), followed by opened state (0x02). The logcat line will appear as: [NOTIFICATION: BA 04 00] for a state change to opening. Refer to [Tedee Lock BLE API Documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html) and navigate to the `NOTIFICATIONS` section from the right menu for more information about notifications.
6. To close the BT connection between the app and the lock, simply click the `Disconnect` button.

> :warning: If you are unable to connect to lock, please check if your mobile certificate did not change. It is deleted after every app uninstall. If it changes, repeat step 4
