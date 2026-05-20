package com.vs.gt.bledemo

import com.goolton.ble.GDBleDevice
import com.goolton.ble.protocol.DeviceInfo

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
