# GD BLE Android Demo

[English](README.en.md)

这是一个面向第三方客户的最小 Android Demo，用于演示如何通过本地 AAR 接入 `GD BLE SDK`，并完成 BLE 眼镜设备的扫描、连接、设备信息读取、方向键控制、文件传输、自定义消息发送和 Wi-Fi 图片查看。

Demo 使用 Kotlin + Jetpack Compose 实现。

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
│       │   ├── FileTransferActivity.kt # 提词器文件传输示例
│       │   ├── CustomMessageActivity.kt # 自定义消息发送示例
│       │   ├── WifiImageActivity.kt # Wi-Fi 图片查看示例
│       │   └── Demo*.kt            # Compose 组件、主题、权限和状态模型
│       └── res/                    # Android 资源文件
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
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
}
```

注意：直接依赖 AAR 时不会自动读取 POM，因此客户项目需要自行添加 SDK 运行所需的第三方依赖，例如 Gson。Demo 使用 Coil 加载 Wi-Fi service 提供的缩略图和原图。

## 权限说明

Demo 已在 `AndroidManifest.xml` 中声明 BLE 和定位权限：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

SDK 不会主动申请运行时权限，调用方 App 需要在扫描或连接前自行申请。Demo 在 `MainActivity` 中演示了 Android 12 及以上和旧版本系统的权限处理。

Wi-Fi 图片查看会访问眼镜端局域网 HTTP 服务，Demo 在 Manifest 中开启了 `android:usesCleartextTraffic="true"`。

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
- 自定义消息发送：
  - 用户输入目标应用包名
  - 用户输入任意字符串
  - 通过 `BleMsg(Action.APP_DATA, pkg, Payload(data = "..."))` 发送
- Wi-Fi 图片查看：
  - 开启眼镜端 Wi-Fi service：`GDBleSdk.startWifiService()`
  - 健康检查：`GET http://{ip}:{port}/health`
  - 图片列表查询：`GDBleSdk.viewMedia("image", page, pageSize)`
  - 缩略图加载：`GET http://{ip}:{port}/thumb/image/{id}`
  - 原图加载：`GET http://{ip}:{port}/raw/image/{id}`
  - 视频列表查询：`GDBleSdk.viewMedia("video", page, pageSize)`
  - 视频预览图加载：`GET http://{ip}:{port}/thumb/video/{id}`
  - 视频整文件下载：`GET http://{ip}:{port}/raw/video/{id}`
  - 视频流式播放/下载：`GET http://{ip}:{port}/stream/video/{id}`
  - 关闭眼镜端 Wi-Fi service：`GDBleSdk.stopWifiService()`
- 提词器文件传输：
  - 文件列表查询：`GDBleSdk.viewFile("com.goolton.teleprompter")`
  - 文件下载：`GDBleSdk.downloadFile(pkg, fileId)`
  - 随机 txt 文件上传：`GDBleSdk.sendFile(file, pkg)`
- 方向键控制：
  - `UP`
  - `DOWN`
  - `LEFT`
  - `RIGHT`
  - `CENTER`
  - `BACK`
  - `HOME`
  - `REFRESH`

## 运行方式

客户可直接下载并安装当前已构建的 Debug APK：

[下载 app-debug.apk](app/build/outputs/apk/debug/app-debug.apk)

如需自行构建，在项目根目录执行：

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

查询提词器文件列表：

```kotlin
GDBleSdk.viewFile("com.goolton.teleprompter")
```

下载提词器文件：

```kotlin
GDBleSdk.downloadFile("com.goolton.teleprompter", fileId)
```

上传提词器文本文件：

```kotlin
GDBleSdk.sendFile(file, "com.goolton.teleprompter")
```

发送自定义应用消息：

```kotlin
val message = BleMsg(
    action = Action.APP_DATA,
    pkg = "com.example.app",
    data = Payload(data = "hello from customer app")
)
GDBleSdk.sendMessage(message)
```

开启 Wi-Fi 图片服务并请求图片列表：

```kotlin
GDBleSdk.startWifiService()

// 收到 Action.WIFI_SERVICE_START 后解析 NetConfig:
val baseUrl = "http://${netConfig.ip}:${netConfig.port}"

GDBleSdk.viewMedia(type = "image", page = 1, pageSize = 100)
```

加载图片 URL：

```kotlin
val thumbUrl = "$baseUrl/thumb/image/${file.id}"
val rawUrl = "$baseUrl/raw/image/${file.id}"
```

视频接口和图片接口流程一致，本 Demo 未单独实现视频页面：

```kotlin
GDBleSdk.viewMedia(type = "video", page = 1, pageSize = 100)

val videoThumbUrl = "$baseUrl/thumb/video/${file.id}"
val videoRawUrl = "$baseUrl/raw/video/${file.id}"
val videoStreamUrl = "$baseUrl/stream/video/${file.id}"
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
- 跳转到文件传输页面
- 跳转到自定义消息页面
- 跳转到 Wi-Fi 图片页面
- 日志展示

### RemoteKeyActivity

方向键页面单独展示按键控制能力，方便第三方客户查看对应 API：

- 显示当前连接状态
- 点击方向键调用 `GDBleSdk.sendKey(...)`
- 输出发送日志和 SDK 回调日志

### FileTransferActivity

文件传输页面演示提词器文件能力，测试包名为 `com.goolton.teleprompter`：

- 查询文件列表
- 下载文件并展示本地缓存路径
- 随机创建 `测试 + 时间戳` 的 txt 文件并上传
- 展示文件传输协议消息和 SDK 回调日志

### CustomMessageActivity

自定义消息页面演示如何向眼镜端指定应用发送字符串：

- 输入目标应用包名
- 输入要放入 `Payload.data` 的字符串
- 发送 `Action.APP_DATA` 协议消息
- 展示最近发送的 JSON、协议回调和错误日志

### WifiImageActivity

Wi-Fi 图片页面演示眼镜端图片服务能力：

- 显示手机本机 IP，并提示手机和眼镜保持在同一网段
- 通过 BLE 打开和关闭眼镜端 Wi-Fi service
- 解析 `NetConfig` 并生成 `http://ip:port` 服务地址
- 健康检查通过后请求图片列表
- 使用 `/thumb/image/{id}` 加载缩略图
- 点击列表项后使用 `/raw/image/{id}` 加载原图
- 视频能力与图片类似，可使用 `/thumb/video/{id}` 预览图、`/raw/video/{id}` 整文件和 `/stream/video/{id}` 流式链接

## 注意事项

- 本仓库只包含 AAR 形式的 SDK，不包含 BLE 协议和认证逻辑源码。
- Demo 仅作为 SDK 接入参考，不包含生产级错误恢复、权限解释弹窗或完整业务 UI。
- BLE 扫描和连接需要真机验证，模拟器通常无法完整验证 BLE 流程。
- 若替换 SDK AAR，请保持 `app/libs/gd-ble-sdk-1.0.0.aar` 文件名或同步修改 `app/build.gradle.kts`。
