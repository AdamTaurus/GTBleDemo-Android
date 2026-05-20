package com.vs.gt.bledemo

import com.goolton.ble.GDBleDevice
import com.goolton.ble.protocol.BleFile
import com.goolton.ble.protocol.DeviceInfo
import com.goolton.ble.protocol.NetConfig

/**
 * Demo 内部使用的连接状态。
 *
 * SDK 对外只暴露 Boolean 连接状态，实际项目可以按自己的业务继续细分为
 * Connecting、Authenticating、Ready 等状态。这里保持简单，方便第三方快速看懂接入流程。
 */
enum class ConnectionUiState {
    Disconnected,
    Connecting,
    Connected,
}

/**
 * MainActivity 展示所需的最小状态集合。
 *
 * 这个类只属于 Demo UI，不属于 SDK。客户接入时可以把同样的信息放进 ViewModel、
 * MVI store、LiveData 或其它状态容器里。
 */
data class MainUiState(
    val scanning: Boolean = false,
    val connectionState: ConnectionUiState = ConnectionUiState.Disconnected,
    val selectedDevice: GDBleDevice? = null,
    val devices: List<GDBleDevice> = emptyList(),
    val deviceInfo: DeviceInfo? = null,
    val logs: List<String> = emptyList(),
)

/**
 * 方向键页面展示所需状态。
 *
 * 方向键 Activity 依赖 SDK 的全局连接状态；进入页面前不需要重新初始化 SDK。
 */
data class RemoteKeyUiState(
    val connected: Boolean = false,
    val logs: List<String> = emptyList(),
)

/**
 * 文件传输页面展示所需状态。
 *
 * 文件列表、下载路径和上传路径都来自 SDK 回调；Demo 不做持久化，客户项目可以按业务需要保存。
 */
data class FileTransferUiState(
    val connected: Boolean = false,
    val files: List<BleFile> = emptyList(),
    val latestUploadPath: String? = null,
    val latestDownloadPath: String? = null,
    val logs: List<String> = emptyList(),
)

/**
 * 自定义消息页面展示所需状态。
 *
 * 这里把用户输入和最近发送的 JSON 一起放在状态里，方便客户复制后迁移到自己的
 * ViewModel 或状态管理方案中。
 */
data class CustomMessageUiState(
    val connected: Boolean = false,
    val packageName: String = "",
    val dataText: String = "",
    val latestSentJson: String? = null,
    val logs: List<String> = emptyList(),
)

/**
 * Wi-Fi 图片页面展示所需状态。
 *
 * 图片列表来自 BLE 的 view_media 协议；缩略图和原图通过眼镜端 Wi-Fi HTTP 服务加载。
 */
data class WifiImageUiState(
    val connected: Boolean = false,
    val localIp: String? = null,
    val netConfig: NetConfig? = null,
    val baseUrl: String? = null,
    val serviceRunning: Boolean = false,
    val checkingService: Boolean = false,
    val loadingImages: Boolean = false,
    val page: Int = 0,
    val total: Int = 0,
    val images: List<BleFile> = emptyList(),
    val selectedImage: BleFile? = null,
    val selectedRawUrl: String? = null,
    val logs: List<String> = emptyList(),
)
