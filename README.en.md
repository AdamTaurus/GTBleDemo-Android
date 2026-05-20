# GD BLE Android Demo

[中文](README.md)

This repository is a minimal Android demo for third-party developers. It shows how to integrate the `GD BLE SDK` as a local AAR and how to scan, connect to BLE glasses, request device information, send remote key events, transfer files, and send custom app messages.

The demo is implemented with Kotlin and Jetpack Compose.

## Project Structure

```text
GTBleDemo/
├── app/
│   ├── libs/
│   │   └── gd-ble-sdk-1.0.0.aar   # GD BLE SDK AAR
│   └── src/main/
│       ├── java/com/vs/gt/bledemo/
│       │   ├── MainActivity.kt      # Scan, connect, device info and feature entries
│       │   ├── RemoteKeyActivity.kt # Remote key control sample
│       │   ├── FileTransferActivity.kt # Teleprompter file transfer sample
│       │   ├── CustomMessageActivity.kt # Custom app message sample
│       │   └── Demo*.kt             # Compose components, theme, permissions and state models
│       └── res/                     # Android resources
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

## Requirements

- Android Studio version that supports AGP 9.x
- JDK 21
- Gradle wrapper included in this repository
- Android minSdk 28
- Android targetSdk 36
- A physical Android device with BLE support

## SDK Integration

The demo depends on the SDK AAR directly:

```kotlin
dependencies {
    implementation(files("libs/gd-ble-sdk-1.0.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")
}
```

When an AAR is referenced directly, Gradle does not read a POM file. Add the SDK runtime dependencies, such as Gson, in the host app.

## Permissions

The demo declares the required BLE and location permissions in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

The SDK does not request runtime permissions. The host app must request the required permissions before scanning or connecting. `MainActivity` demonstrates the permission flow for Android 12 and later, as well as older Android versions.

## Implemented Features

- SDK initialization: `GDBleSdk.init(applicationContext)`
- SDK listener registration: `GDBleSdk.addListener(...)`
- BLE scanning: `GDBleSdk.startScan(timeoutMs)`
- Stop scanning: `GDBleSdk.stopScan()`
- Device connection: `GDBleSdk.connect(device)`
- Manual disconnect: `GDBleSdk.disconnect(reason)`
- Connection status display
- Scanned device list
- Device info request: `GDBleSdk.getDeviceInfo()`
- Raw protocol JSON logs
- Parsed protocol message logs
- File receive callback logs
- Custom app message sending:
  - User-entered target app package name
  - User-entered string payload
  - Send with `BleMsg(Action.APP_DATA, pkg, Payload(data = "..."))`
- Teleprompter file transfer:
  - Query file list: `GDBleSdk.viewFile("com.goolton.teleprompter")`
  - Download file: `GDBleSdk.downloadFile(pkg, fileId)`
  - Upload a generated txt file: `GDBleSdk.sendFile(file, pkg)`
- Remote key control:
  - `UP`
  - `DOWN`
  - `LEFT`
  - `RIGHT`
  - `CENTER`
  - `BACK`
  - `HOME`
  - `REFRESH`

## Build and Run

Build the debug APK from the repository root:

```bash
./gradlew :app:assembleDebug
```

Install the APK:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio and run the `app` configuration on a physical Android device.

## Basic SDK Flow

Initialize the SDK:

```kotlin
GDBleSdk.init(applicationContext)
GDBleSdk.addListener(listener)
```

Start scanning:

```kotlin
GDBleSdk.startScan(12_000L)
```

Connect to a scanned device:

```kotlin
GDBleSdk.connect(device)
```

Request device information after connection:

```kotlin
GDBleSdk.getDeviceInfo()
```

Send remote key events:

```kotlin
GDBleSdk.sendKey(GDBleKey.UP)
GDBleSdk.sendKey(GDBleKey.CENTER)
GDBleSdk.sendKey(GDBleKey.BACK)
```

Query the teleprompter file list:

```kotlin
GDBleSdk.viewFile("com.goolton.teleprompter")
```

Download a teleprompter file:

```kotlin
GDBleSdk.downloadFile("com.goolton.teleprompter", fileId)
```

Upload a teleprompter text file:

```kotlin
GDBleSdk.sendFile(file, "com.goolton.teleprompter")
```

Send a custom app message:

```kotlin
val message = BleMsg(
    action = Action.APP_DATA,
    pkg = "com.example.app",
    data = Payload(data = "hello from customer app")
)
GDBleSdk.sendMessage(message)
```

Remove the listener when the page is destroyed:

```kotlin
GDBleSdk.removeListener(listener)
```

## Screens

### MainActivity

The main screen focuses on the basic connection flow and feature entries:

- SDK initialization
- Runtime permission request
- BLE scan and stop scan
- Device list
- Connect and disconnect
- Device info display
- Entry to the remote key screen
- Entry to the file transfer screen
- Entry to the custom message screen
- Logs

### RemoteKeyActivity

The remote key screen demonstrates the key control API:

- Shows current connection status
- Calls `GDBleSdk.sendKey(...)` when a key is tapped
- Shows sent key logs and SDK callback logs

### FileTransferActivity

The file transfer screen demonstrates teleprompter file APIs with the test package `com.goolton.teleprompter`:

- Query file list
- Download a file and show the local cache path
- Generate and upload a timestamped txt test file
- Show file transfer protocol messages and SDK callback logs

### CustomMessageActivity

The custom message screen demonstrates how to send a string to a target app on the glasses:

- Enter the target app package name
- Enter the string stored in `Payload.data`
- Send an `Action.APP_DATA` protocol message
- Show the latest sent JSON, protocol callbacks, and error logs

## Notes

- This repository contains the SDK only as an AAR. It does not include BLE protocol or authentication source code.
- This demo is for SDK integration reference only. It does not include production-level error recovery, permission rationale dialogs, or complete business UI.
- BLE scanning and connection should be verified on a physical device. Emulators usually cannot validate the full BLE flow.
- If you replace the SDK AAR, keep the file name `app/libs/gd-ble-sdk-1.0.0.aar` or update `app/build.gradle.kts` accordingly.
