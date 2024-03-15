# Tedee Lock communication example

![lock](https://user-images.githubusercontent.com/81370389/209109383-c9163001-cc5b-418b-be65-87906a3cc11c.jpg)

## Documentation

[Mobile SDK Documentation](https://tedee-com.github.io/tedee-mobile-sdk-android/)

## About

This example project was created by the [Tedee](https://tedee.com) team to show you how to operate the Tedee Lock using Bluetooth Low Energy communication protocol.
This project is developed using the Kotlin language and is designed to run on Android devices. It utilizes our Tedee Lock SDK, which can be accessed via the following link: [LINK TO SDK](https://github.com/tedee-com/tedee-mobile-sdk-android).

In example app navigate to the `build.gradle` file located in the Module: app directory. Within this file, locate the dependencies section where you'll find the dependency for the Tedee Lock SDK. `implementation("com.tedee:android-ble-sdk:<LATEST_VERSION>`

The purpose of this project is to demonstrate how you can integrate the Tedee Lock SDK into your own app. It provides you with the capability to:
- Establish a Bluetooth connection with a Tedee Lock by providing necessary data such as the Lock's Serial Number, Certificate, Mobile Public Key, Device ID and  Device Name.
- Disconnect the connection from the lock.
- Send commands to the lock based on the BLE documentation.
- Receive callbacks when the connection status changes.
- Receive callbacks when indication messages are received.
- Receive callbacks when notification messages are received.
- Handle errors that occur during lock connection.

App uses RxAndroidBle library and custom implementation of secure BLE session (All from Tedee Lock SDK). It is not using any Tedee services and works only locally within the range of BLE. The example app uses an API to create a certificate right from the app itself. When setting up, you will need to manually obtain the Personal Access Key.

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
When you first open the app, it will generate a `Mobile Public Key`. This key is necessary for creating a lock certificate, which will be covered in the following steps. To find this key, check the Logcat at the bottom of Android Studio and look for the entry marked `!!! Public key to register mobile:`. The app will save this key after its initial creation. In future app launches, it will retrieve the key from its stored location.
Please keep an eye on the Logcat as it will display the sequence of actions the app performs to the lock. These actions include connecting to the lock, starting an encrypted session, sending any commands, and receiving a response.

### Step 4 - Create Personal Access Key
1. Log in to [Tedee Portal](https://portal.tedee.com) with credentials from created Tedee account
2. Click on your initials in top right corner

![img2](https://user-images.githubusercontent.com/81370389/209111859-9c022725-1593-4bfd-9d71-72b9e58d4397.png)

3. Click on Personal Access Keys and generate new access key with at least **Device certificates - Read** scope. Remember to save this key, as it will be needed for the following step.

### Step 5 - Setting Up the Personal Access Key and Running the App
1. In the project navigator, open the `Constants.kt` file (located within tedee.mobile.demo).
2. Replace the `PERSONAL_ACCESS_KEY` value with the one obtained in the previous step.
3. Compile and run app again with `Run 'app'` button or use `Shift + F10`.

### Step 6 - Entering Required Information
> :warning: Before you can generate a certificate and connect to the lock, ensure all necessary information is entered

1. `Serial Number`: Enter the serial number of your Tedee lock. This is essential for connecting to the lock. You can find it in the Tedee app under Lock > Settings > Information > Serial number.
2. `Lock Name`: Input the name of your Tedee lock. This is necessary to generate a certificate for the lock. Locate this in the Tedee app under Lock > Settings > Lock name.
3. `Device ID`: Enter the device ID of your Tedee lock. This is required to generate a certificate for the lock. Find this information in the Tedee app under Lock > Settings > Information > Device ID.

### Step 7 - Generating the Certificate
1. Ensure you have an internet connection and all data from the previous step has been filled in.
2. The `Personal Access Key` is required for creating a `MobileID` and generating a `certificate`. Click the `Generate certificate` button to proceed. If successful, you will see `Certificate: Generate certificate request successful` below the `Keep Connection` switch. If the request fails, you will see `Certificate: [ERROR MESSAGE]`. If there will be an error, ensure you have an internet connection, and that the Serial Number, Lock Name, and Device ID are correctly entered and match those from the Tedee app.
3. If you continue to encounter issues with certificate generation, refer to the additional troubleshooting step (`Step 8`).
4. The Certificate, Device Public Key, and Mobile Public Key are saved in the device's cache. For future app launches, you won't need to regenerate the token; you can skip directly to (`Step 10`)

> :warning: The generated certificate has an expiration date included in the response. Once expired, you will not be able to operate the lock and will need to acquire a new one. In this case, simply click `Generate token`.

### Step 8.1 (Optional - in case when Step 7 will not success) - Register Tedee example app
1. Go to [Tedee API](https://api.tedee.com) and authorize yourself with created `Personal Access Key` (from Step 4)
2. Click "Authorize" button

![img9](https://user-images.githubusercontent.com/81370389/209112240-01764c21-16c8-4b42-86a6-5ee2374b81d7.png)

3. Proper format is `PersonalKey [YOUR PERSONAL ACCESS KEY]`
4. Confirm with `Authorize`

> :information_source: You can also use one-time token by signing in after clicking "Azure B2C Login Page" on top of Swagger website. Paste returned token similarly as in step 6. in `Bearer [TOKEN]` format

5. Go to `Mobile` section and use `POST /api/[api version]/my/mobile` route and click on `Try it out` button

![img13](https://user-images.githubusercontent.com/81370389/209114544-3764f0f9-0a03-41a7-bb67-426e1514f154.png)

6. Enter `name` (lock name from Tedee app), `operatingSystem` set to `3` and `publicKey` the one that was copied in "First launch" step
7. Response will return `id` that is required in next step (as `MobileID`)
8. Go to `DeviceCertificate` section and use `/api/[api version]/my/devicecertificate/getformobile`

![img10](https://user-images.githubusercontent.com/81370389/209114588-68facc13-b162-48f7-baaa-6dd605b2228a.png)

9. Click on `Try it out`
10. Fill `MobileID` gathered from previous request response
11. Fill `DeviceId` gathered from Tedee app (click: Lock > Settings > Information > Device ID)
12. Click `Execute`, store somewhere response result, you will need it in next step

> :warning: Generated certificate has expiration date, which is attached to the response with certificate. After certificate expiration you will not be able to operate the lock and you need to get new one.

### Step 8.2 (Optional - in case when Step 7 will not success) - Add Certificate to Project

1. Open MainActivity.kt in project navigator
2. Replace value of `certificate` with `result.certificate` of API request
3. Replace value of `devicePublicKey` with `result.devicePublicKey` of API request
4. Comment out the `setupCertificateData` method call to prevent its execution.
5. Compile and run app again with `Run 'app'` button or use `Shift + F10`. You won't need to click `Generate certificate` again, as you now have the necessary information.

### Step 9 - Choose whether to keep the connection with the lock or not

> :warning: By default, the connection method from the Tedee Lock SDK has the keepConnection parameter set to false.

1. To modify the keepConnection setting, use the switch found directly below the `Connect` button.
2. Turning the switch on allows the app to maintain the connection indefinitely.
3. Turning the switch off sets the app to keep the connection for a limited time only.

### Step 10 - Connecting to the lock
1. Click the `Connect` button.
2. During the connection process, the status will display `Connecting`. Once the connection is successfully established, it will change to `Secure session established`. If any issues occurs, the status will show `Disconnected`.

> :warning: If you are unable to connect to lock, please check if your mobile certificate did not change or was not expired. It is deleted after every app uninstall. If it changes, repeat step 7

### Step 11 - Send a command to the lock
1. Once the app is connected to the lock, you'll see two fields and a `SEND` button. In the first EditText, you can input the `LOCK COMMAND` (e.g., 0x51 to open the lock). The second EditText is optional and allows you to input parameters to be sent with the command (e.g., 0x02 to force unlock). You can input multiple values in the params field by separating them with spaces (e.g., 0x02 0x04). For BLE commands documentation, refer to [Tedee Lock BLE API Documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html) and navigate to the `COMMANDS` section from the right menu.
2. The app should execute the command, and you can observe the Logcat or history of the commands/notifications/errors at the bottom of the example app screen. Messages tagged with `MESSAGE:` indicate BT messages sent by the mobile device, while messages tagged with `LOCK LISTENER message:` indicate BT responses from the lock. For instance, the unlock command has a value of 51. The response may contain additional parameters. If the parameter is 00, the lock successfully initiated the operation. For more information, refer to [Tedee Lock BLE API Documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html).

![img15](https://user-images.githubusercontent.com/81370389/209112722-46611d90-0556-4725-8e9e-9b80aae2531c.png)

3. The app listens for notifications from the lock. Check the Logcat for notifications tagged with `NOTIFICATION:`. For example, after sending the unlock command (0x51), the app listens for notifications and receives lock state changes if the operation is successful. The first state is opening state (0x04), followed by opened state (0x02). The logcat line will appear as: [NOTIFICATION: BA 04 00] for a state change to opening. Refer to [Tedee Lock BLE API Documentation](https://tedee-tedee-lock-ble-api-doc.readthedocs-hosted.com/en/latest/index.html) and navigate to the `NOTIFICATIONS` section from the right menu for more information about notifications.
4. To close the BT connection between the app and the lock, simply click the `Disconnect` button.