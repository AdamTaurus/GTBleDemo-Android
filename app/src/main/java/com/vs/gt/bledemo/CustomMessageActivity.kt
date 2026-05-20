package com.vs.gt.bledemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goolton.ble.GDBleDevice
import com.goolton.ble.GDBleListener
import com.goolton.ble.GDBleSdk
import com.goolton.ble.protocol.Action
import com.goolton.ble.protocol.BleMsg
import com.goolton.ble.protocol.GsonHolder
import com.goolton.ble.protocol.Payload

class CustomMessageActivity : ComponentActivity() {

    private var uiState by mutableStateOf(CustomMessageUiState())

    /**
     * 自定义消息页面只演示应用层字符串发送。
     *
     * 注册 listener 的目的不是发送所必需，而是让客户能看到发送后的协议响应、原始 JSON
     * 和错误回调。页面销毁时必须移除 listener，避免 Activity 释放后继续收到 UI 回调。
     */
    private val sdkListener = object : GDBleListener {
        override fun onScanStateChanged(scanning: Boolean) = Unit

        override fun onDeviceFound(device: GDBleDevice) = Unit

        override fun onConnectionStateChanged(connected: Boolean) {
            runOnUiThread {
                uiState = uiState.copy(connected = connected)
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

        // MainActivity 已经初始化过 SDK；重复 init 是安全的，方便客户复制这个 Activity 单独参考。
        GDBleSdk.init(applicationContext)
        GDBleSdk.addListener(sdkListener)
        uiState = uiState.copy(connected = GDBleSdk.isConnected())
        appendLog(getString(R.string.log_custom_message_page_opened))

        setContent {
            GTBleDemoTheme {
                CustomMessageScreen(
                    state = uiState,
                    onBackClick = ::finish,
                    onPackageNameChange = { value ->
                        uiState = uiState.copy(packageName = value)
                    },
                    onDataTextChange = { value ->
                        uiState = uiState.copy(dataText = value)
                    },
                    onSendClick = ::sendCustomMessage,
                )
            }
        }
    }

    override fun onDestroy() {
        GDBleSdk.removeListener(sdkListener)
        super.onDestroy()
    }

    private fun sendCustomMessage() {
        if (!GDBleSdk.isConnected()) {
            appendLog(getString(R.string.log_custom_message_skip_not_connected))
            uiState = uiState.copy(connected = false)
            return
        }

        val pkg = uiState.packageName.trim()
        if (pkg.isEmpty()) {
            appendLog(getString(R.string.log_custom_message_empty_pkg))
            return
        }

        if (uiState.dataText.isBlank()) {
            appendLog(getString(R.string.log_custom_message_empty_data))
            return
        }

        // 自定义业务字符串放进 Payload.data，pkg 指定眼镜端要转发给哪个应用。
        val message = BleMsg(
            action = Action.APP_DATA,
            pkg = pkg,
            data = Payload(data = uiState.dataText),
        )
        val json = GsonHolder.gson.toJson(message)
        uiState = uiState.copy(latestSentJson = json)
        appendLog(getString(R.string.log_custom_message_send, pkg))
        GDBleSdk.sendMessage(message)
    }

    private fun appendLog(message: String) {
        uiState = uiState.copy(logs = (listOf(demoLogLine(message)) + uiState.logs).take(100))
    }

    private fun localizedBoolean(value: Boolean): String {
        return getString(if (value) R.string.boolean_true else R.string.boolean_false)
    }
}

@Composable
private fun CustomMessageScreen(
    state: CustomMessageUiState,
    onBackClick: () -> Unit,
    onPackageNameChange: (String) -> Unit,
    onDataTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.custom_message_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.custom_message_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            CustomMessageActionPanel(
                connected = state.connected,
                packageName = state.packageName,
                dataText = state.dataText,
                onBackClick = onBackClick,
                onPackageNameChange = onPackageNameChange,
                onDataTextChange = onDataTextChange,
                onSendClick = onSendClick,
            )
        }

        item {
            SectionTitle(stringResource(R.string.section_custom_message_preview))
            CustomMessagePreviewPanel(json = state.latestSentJson)
        }

        item {
            SectionTitle(stringResource(R.string.section_logs))
            LogPanel(logs = state.logs)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CustomMessageActionPanel(
    connected: Boolean,
    packageName: String,
    dataText: String,
    onBackClick: () -> Unit,
    onPackageNameChange: (String) -> Unit,
    onDataTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    DemoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                text = if (connected) {
                    stringResource(R.string.status_connected)
                } else {
                    stringResource(R.string.status_disconnected)
                }
            )
        }
        OutlinedTextField(
            value = packageName,
            onValueChange = onPackageNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.custom_message_pkg_label)) },
            placeholder = { Text(stringResource(R.string.custom_message_pkg_placeholder)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
        )
        OutlinedTextField(
            value = dataText,
            onValueChange = onDataTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            label = { Text(stringResource(R.string.custom_message_data_label)) },
            placeholder = { Text(stringResource(R.string.custom_message_data_placeholder)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
        )
        RowButton(
            text = stringResource(R.string.button_send_custom_message),
            modifier = Modifier.fillMaxWidth(),
            enabled = connected,
            onClick = onSendClick,
        )
        RowOutlinedButton(
            text = stringResource(R.string.button_back_to_main),
            modifier = Modifier.fillMaxWidth(),
            onClick = onBackClick,
        )
    }
}

@Composable
private fun CustomMessagePreviewPanel(json: String?) {
    DemoCard {
        Text(
            text = json ?: stringResource(R.string.custom_message_no_preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
