# GD BLE Android Demo

这是一个面向第三方客户的最小 Android Demo，用于演示如何通过本地 AAR 接入 `GD BLE SDK`，并完成 BLE 眼镜设备的扫描、连接、设备信息读取和方向键控制。

Demo 使用 Kotlin + Jetpack Compose 实现，默认语言为中文，并提供英文资源翻译。

## 项目结构

```text
GTBleDemo/
├── app/
│   ├── libs/
│   │   └── gd-ble-sdk-1.0.0.aar   # GD BLE SDK AAR
│   └── src/main/
│       ├── java/com/vs/gt/bledemo/
│       │   ├── MainActivity.kt     # 扫描、连接、设备信息和功能入口
│       │   ├── RemoteKeyActivity.kt # 方向键控制示例
│       │   └── Demo*.kt            # Compose 组件、主题、权限和状态模型
│       └── res/
│           ├── values/strings.xml  # 默认中文文案
│           └── values-en/strings.xml # 英文文案
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

## 环境要求

- Android Studio：建议使用支持 AGP 9.x 的版本
- JDK：21
- Gradle：项目内置 wrapper
- Android minSdk：28
- Android targetSdk：36
- 真机需要支持 BLE

## SDK 接入方式

当前 Demo 直接依赖本地 AAR：

```kotlin
dependencies {
    implementation(files("libs/gd-ble-sdk-1.0.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")
}
```

注意：直接依赖 AAR 时不会自动读取 POM，因此客户项目需要自行添加 SDK 运行所需的第三方依赖，例如 Gson。

## 权限说明

Demo 已在 `AndroidManifest.xml` 中声明 BLE 和定位权限：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

SDK 不会主动申请运行时权限，调用方 App 需要在扫描或连接前自行申请。Demo 在 `MainActivity` 中演示了 Android 12 及以上和旧版本系统的权限处理。

## 已实现功能

- SDK 初始化：`GDBleSdk.init(applicationContext)`
- 监听 SDK 回调：`GDBleSdk.addListener(...)`
- BLE 扫描：`GDBleSdk.startScan(timeoutMs)`
- 停止扫描：`GDBleSdk.stopScan()`
- 设备连接：`GDBleSdk.connect(device)`
- 主动断开：`GDBleSdk.disconnect(reason)`
- 连接状态展示
- 扫描设备列表展示
- 设备信息请求：`GDBleSdk.getDeviceInfo()`
- 原始协议 JSON 日志展示
- 协议消息回调日志展示
- 文件接收回调日志展示
- 方向键控制：
  - `UP`
  - `DOWN`
  - `LEFT`
  - `RIGHT`
  - `CENTER`
  - `BACK`
  - `HOME`
  - `REFRESH`
- 中英文文案资源切换

## 运行方式

在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

构建成功后安装：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开项目，选择 `app` 运行到 Android 真机。

## 基础调用流程

初始化 SDK：

```kotlin
GDBleSdk.init(applicationContext)
GDBleSdk.addListener(listener)
```

开始扫描：

```kotlin
GDBleSdk.startScan(12_000L)
```

连接扫描到的设备：

```kotlin
GDBleSdk.connect(device)
```

连接后读取设备信息：

```kotlin
GDBleSdk.getDeviceInfo()
```

发送方向键：

```kotlin
GDBleSdk.sendKey(GDBleKey.UP)
GDBleSdk.sendKey(GDBleKey.CENTER)
GDBleSdk.sendKey(GDBleKey.BACK)
```

页面销毁时移除监听：

```kotlin
GDBleSdk.removeListener(listener)
```

## 页面说明

### MainActivity

主页面只负责基础连接流程和功能入口：

- SDK 初始化
- 运行时权限申请
- BLE 扫描和停止扫描
- 设备列表展示
- 连接和断开
- 设备信息展示
- 跳转到方向键页面
- 日志展示

### RemoteKeyActivity

方向键页面单独展示按键控制能力，方便第三方客户查看对应 API：

- 显示当前连接状态
- 点击方向键调用 `GDBleSdk.sendKey(...)`
- 输出发送日志和 SDK 回调日志

## 多语言说明

默认资源位于：

```text
app/src/main/res/values/strings.xml
```

英文翻译位于：

```text
app/src/main/res/values-en/strings.xml
```

如需增加其它语言，按 Android 标准资源目录新增对应 `values-xx/strings.xml` 即可。

## 注意事项

- 本仓库只包含 AAR 形式的 SDK，不包含 BLE 协议和认证逻辑源码。
- Demo 仅作为 SDK 接入参考，不包含生产级错误恢复、权限解释弹窗或完整业务 UI。
- BLE 扫描和连接需要真机验证，模拟器通常无法完整验证 BLE 流程。
- 若替换 SDK AAR，请保持 `app/libs/gd-ble-sdk-1.0.0.aar` 文件名或同步修改 `app/build.gradle.kts`。

