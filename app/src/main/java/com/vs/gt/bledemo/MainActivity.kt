package com.vs.gt.bledemo

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.gson.JsonParser
import com.goolton.ble.GDBleDevice
import com.goolton.ble.GDBleListener
import com.goolton.ble.GDBleSdk
import com.goolton.ble.protocol.Action
import com.goolton.ble.protocol.BleMsg
import com.goolton.ble.protocol.DeviceInfo
import com.goolton.ble.protocol.GsonHolder

class MainActivity : ComponentActivity() {

    private val devices = mutableStateMapOf<String, GDBleDevice>()
    private var uiState by mutableStateOf(MainUiState())

    /**
     * Demo 里使用 Activity Result API 申请权限。
     *
     * SDK 只负责 BLE 通讯，不负责权限 UI；第三方 App 应该在自己的页面中完成授权流程。
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { granted -> !granted }.keys
            if (denied.isEmpty()) {
                appendLog(getString(R.string.log_permissions_granted))
                startScan()
            } else {
                appendLog(getString(R.string.log_permissions_denied, denied.joinToString()))
            }
        }

    /**
     * SDK 通过 listener 把扫描、连接、协议消息等事件回调给业务层。
     *
     * Activity 在 onCreate 注册，在 onDestroy 反注册，避免页面销毁后继续收到 UI 回调。
     */
    private val sdkListener = object : GDBleListener {
        override fun onScanStateChanged(scanning: Boolean) {
            runOnUiThread {
                uiState = uiState.copy(scanning = scanning)
                appendLog(getString(R.string.log_scan_state, localizedBoolean(scanning)))
            }
        }

        override fun onDeviceFound(device: GDBleDevice) {
            runOnUiThread {
                if (!devices.containsKey(device.address)) {
                    appendLog(getString(R.string.log_found_device, device.localizedDisplayName()))
                }
                devices[device.address] = device
                uiState = uiState.copy(devices = devices.values.toList())
            }
        }

        override fun onConnectionStateChanged(connected: Boolean) {
            runOnUiThread {
                uiState = uiState.copy(
                    connectionState = if (connected) {
                        ConnectionUiState.Connected
                    } else {
                        ConnectionUiState.Disconnected
                    },
                )
                appendLog(getString(R.string.log_connected, localizedBoolean(connected)))
            }
        }

        override fun onMessageReceived(message: BleMsg) {
            runOnUiThread {
                appendLog(getString(R.string.log_message, message.action, message.cmd))
            }
        }

        override fun onRawMessageReceived(json: String) {
            runOnUiThread {
                appendLog(getString(R.string.log_raw, json))
                parseDeviceInfo(json)?.let { info ->
                    uiState = uiState.copy(deviceInfo = info)
                    appendLog(getString(R.string.log_device_info_updated))
                }
            }
        }

        override fun onFileReceived(absolutePath: String) {
            runOnUiThread {
                appendLog(getString(R.string.log_file_received, absolutePath))
            }
        }

        override fun onError(error: Throwable) {
            runOnUiThread {
                appendLog(getString(R.string.log_error, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SDK 初始化只需要 applicationContext，避免 Activity 泄漏。
        GDBleSdk.init(applicationContext)
        GDBleSdk.addListener(sdkListener)
        syncCurrentConnectionState()
        appendLog(getString(R.string.log_sdk_initialized))

        setContent {
            GTBleDemoTheme {
                MainScreen(
                    state = uiState,
                    onScanClick = ::ensureReadyAndScan,
                    onStopScanClick = { GDBleSdk.stopScan() },
                    onDisconnectClick = { GDBleSdk.disconnect(getString(R.string.disconnect_reason_demo)) },
                    onConnectClick = ::connectDevice,
                    onRefreshDeviceInfoClick = ::requestDeviceInfo,
                    onOpenRemoteKeyClick = {
                        startActivity(Intent(this, RemoteKeyActivity::class.java))
                    },
                    onOpenFileTransferClick = {
                        startActivity(Intent(this, FileTransferActivity::class.java))
                    },
                    onOpenCustomMessageClick = {
                        startActivity(Intent(this, CustomMessageActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        GDBleSdk.removeListener(sdkListener)
        super.onDestroy()
    }

    private fun syncCurrentConnectionState() {
        uiState = uiState.copy(
            scanning = GDBleSdk.isScanning(),
            connectionState = if (GDBleSdk.isConnected()) {
                ConnectionUiState.Connected
            } else {
                ConnectionUiState.Disconnected
            },
        )
    }

    private fun ensureReadyAndScan() {
        if (!GDBleSdk.isBluetoothEnabled()) {
            appendLog(getString(R.string.log_bluetooth_disabled))
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        val missing = requiredBlePermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            appendLog(getString(R.string.log_request_permissions, missing.joinToString()))
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        startScan()
    }

    private fun startScan() {
        devices.clear()
        uiState = uiState.copy(devices = emptyList(), selectedDevice = null, deviceInfo = null)
        appendLog(getString(R.string.log_start_scan))
        GDBleSdk.startScan(12_000L)
    }

    private fun connectDevice(device: GDBleDevice) {
        uiState = uiState.copy(
            selectedDevice = device,
            connectionState = ConnectionUiState.Connecting,
            deviceInfo = null,
        )
        appendLog(getString(R.string.log_connect_device, device.address))
        GDBleSdk.connect(device)
    }

    private fun requestDeviceInfo() {
        if (!GDBleSdk.isConnected()) {
            appendLog(getString(R.string.log_skip_device_info_not_connected))
            return
        }
        appendLog(getString(R.string.log_request_device_info))
        GDBleSdk.getDeviceInfo()
    }

    private fun appendLog(message: String) {
        uiState = uiState.copy(logs = (listOf(demoLogLine(message)) + uiState.logs).take(80))
    }

    private fun localizedBoolean(value: Boolean): String {
        return getString(if (value) R.string.boolean_true else R.string.boolean_false)
    }

    private fun GDBleDevice.localizedDisplayName(): String {
        return getString(
            R.string.device_display_name,
            name ?: getString(R.string.unknown_device),
            address,
            rssi,
        )
    }

    /**
     * 设备信息在协议里通过 Raw JSON 回来。
     *
     * SDK 已经把协议消息也解析成 BleMsg 回调给 onMessageReceived；这里为了演示 DeviceInfo
     * 的读取方式，直接从 raw JSON 的 data.data 字段再解析成公开模型 DeviceInfo。
     */
    private fun parseDeviceInfo(json: String): DeviceInfo? {
        return try {
            val msg = GsonHolder.gson.fromJson(json, BleMsg::class.java)
            if (msg.action != Action.DEVICE_INFO) return null
            val rawData = msg.data?.data ?: return null
            if (rawData.trim().startsWith("{")) {
                GsonHolder.gson.fromJson(rawData, DeviceInfo::class.java)
            } else {
                val jsonElement = JsonParser.parseString(rawData)
                GsonHolder.gson.fromJson(jsonElement, DeviceInfo::class.java)
            }
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
private fun MainScreen(
    state: MainUiState,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onConnectClick: (GDBleDevice) -> Unit,
    onRefreshDeviceInfoClick: () -> Unit,
    onOpenRemoteKeyClick: () -> Unit,
    onOpenFileTransferClick: () -> Unit,
    onOpenCustomMessageClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.main_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.main_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ConnectionPanel(
                state = state,
                onScanClick = onScanClick,
                onStopScanClick = onStopScanClick,
                onDisconnectClick = onDisconnectClick,
                onRefreshDeviceInfoClick = onRefreshDeviceInfoClick,
                onOpenRemoteKeyClick = onOpenRemoteKeyClick,
                onOpenFileTransferClick = onOpenFileTransferClick,
                onOpenCustomMessageClick = onOpenCustomMessageClick,
            )
        }

        item {
            SectionTitle(stringResource(R.string.section_devices))
        }

        if (state.devices.isEmpty()) {
            item {
                DemoCard {
                    Text(
                        text = stringResource(R.string.empty_devices),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.devices, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    selected = state.selectedDevice?.address == device.address,
                    onConnectClick = { onConnectClick(device) },
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.section_device_info))
            DeviceInfoPanel(info = state.deviceInfo)
        }

        item {
            SectionTitle(stringResource(R.string.section_logs))
            LogPanel(logs = state.logs)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConnectionPanel(
    state: MainUiState,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onRefreshDeviceInfoClick: () -> Unit,
    onOpenRemoteKeyClick: () -> Unit,
    onOpenFileTransferClick: () -> Unit,
    onOpenCustomMessageClick: () -> Unit,
) {
    val connected = state.connectionState == ConnectionUiState.Connected
    DemoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                text = when (state.connectionState) {
                    ConnectionUiState.Disconnected -> stringResource(R.string.status_disconnected)
                    ConnectionUiState.Connecting -> stringResource(R.string.status_connecting)
                    ConnectionUiState.Connected -> stringResource(R.string.status_connected)
                }
            )
            StatusChip(
                text = if (state.scanning) {
                    stringResource(R.string.status_scanning)
                } else {
                    stringResource(R.string.status_idle)
                }
            )
        }
        state.selectedDevice?.let { device ->
            Text(
                text = deviceDisplayName(device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionRow {
            RowButton(
                text = if (state.scanning) {
                    stringResource(R.string.status_scanning)
                } else {
                    stringResource(R.string.button_scan)
                },
                modifier = Modifier.weight(1f),
                enabled = !state.scanning,
                onClick = onScanClick,
            )
            RowOutlinedButton(
                text = stringResource(R.string.button_stop),
                modifier = Modifier.weight(1f),
                enabled = state.scanning,
                onClick = onStopScanClick,
            )
            RowOutlinedButton(
                text = stringResource(R.string.button_disconnect),
                modifier = Modifier.weight(1f),
                enabled = connected,
                onClick = onDisconnectClick,
            )
        }
        ActionRow {
            RowButton(
                text = stringResource(R.string.button_device_info),
                modifier = Modifier.weight(1f),
                enabled = connected,
                onClick = onRefreshDeviceInfoClick,
            )
            RowButton(
                text = stringResource(R.string.button_remote_keys),
                modifier = Modifier.weight(1f),
                enabled = connected,
                onClick = onOpenRemoteKeyClick,
            )
        }
        RowButton(
            text = stringResource(R.string.button_file_transfer),
            modifier = Modifier.fillMaxWidth(),
            enabled = connected,
            onClick = onOpenFileTransferClick,
        )
        RowButton(
            text = stringResource(R.string.button_custom_message),
            modifier = Modifier.fillMaxWidth(),
            enabled = connected,
            onClick = onOpenCustomMessageClick,
        )
    }
}

@Composable
private fun DeviceRow(
    device: GDBleDevice,
    selected: Boolean,
    onConnectClick: () -> Unit,
) {
    DemoCard {
        Text(
            text = device.name ?: stringResource(R.string.unknown_device),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.device_rssi, device.address, device.rssi),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowButton(
            text = if (selected) {
                stringResource(R.string.button_reconnect)
            } else {
                stringResource(R.string.button_connect)
            },
            modifier = Modifier.fillMaxWidth(),
            onClick = onConnectClick,
        )
    }
}

@Composable
private fun DeviceInfoPanel(info: DeviceInfo?) {
    DemoCard {
        if (info == null) {
            Text(
                text = stringResource(R.string.device_info_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DemoCard
        }
        Text(
            text = stringResource(R.string.device_info_serial, info.serial),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.device_info_battery, info.battery),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.device_info_charging,
                localizedBooleanString(info.isCharging),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.device_info_network_connected,
                localizedBooleanString(info.netWorkConnected),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.device_info_apps,
                if (info.apps.isEmpty()) {
                    stringResource(R.string.device_info_apps_none)
                } else {
                    info.apps.joinToString()
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun deviceDisplayName(device: GDBleDevice): String {
    return stringResource(
        R.string.device_display_name,
        device.name ?: stringResource(R.string.unknown_device),
        device.address,
        device.rssi,
    )
}

@Composable
private fun localizedBooleanString(value: Boolean): String {
    return stringResource(if (value) R.string.boolean_true else R.string.boolean_false)
}
