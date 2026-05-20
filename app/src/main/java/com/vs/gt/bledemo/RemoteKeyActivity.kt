package com.vs.gt.bledemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goolton.ble.GDBleKey
import com.goolton.ble.GDBleListener
import com.goolton.ble.GDBleSdk
import com.goolton.ble.protocol.BleMsg

class RemoteKeyActivity : ComponentActivity() {

    private var uiState by mutableStateOf(RemoteKeyUiState())

    /**
     * 方向键页面只关心连接状态和 SDK 错误。
     *
     * 如果业务页面还需要展示眼镜端返回的协议消息，也可以继续在这里实现
     * onMessageReceived/onRawMessageReceived。
     */
    private val sdkListener = object : GDBleListener {
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

        override fun onError(error: Throwable) {
            runOnUiThread {
                appendLog(getString(R.string.log_error, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MainActivity 已经初始化过 SDK；这里再次调用 init 也是安全的，方便客户复制单页示例。
        GDBleSdk.init(applicationContext)
        GDBleSdk.addListener(sdkListener)
        uiState = uiState.copy(connected = GDBleSdk.isConnected())
        appendLog(getString(R.string.log_remote_page_opened))

        setContent {
            GTBleDemoTheme {
                RemoteKeyScreen(
                    state = uiState,
                    onBackClick = ::finish,
                    onSendKey = ::sendKey,
                )
            }
        }
    }

    override fun onDestroy() {
        GDBleSdk.removeListener(sdkListener)
        super.onDestroy()
    }

    private fun sendKey(key: GDBleKey) {
        if (!GDBleSdk.isConnected()) {
            appendLog(getString(R.string.log_skip_key_not_connected, key))
            return
        }

        // 方向键最终会被 SDK 转成协议 JSON 并通过 BLE 控制通道发送给眼镜端。
        appendLog(getString(R.string.log_send_key, key))
        GDBleSdk.sendKey(key)
    }

    private fun appendLog(message: String) {
        uiState = uiState.copy(logs = (listOf(demoLogLine(message)) + uiState.logs).take(80))
    }

    private fun localizedBoolean(value: Boolean): String {
        return getString(if (value) R.string.boolean_true else R.string.boolean_false)
    }
}

@Composable
private fun RemoteKeyScreen(
    state: RemoteKeyUiState,
    onBackClick: () -> Unit,
    onSendKey: (GDBleKey) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.remote_key_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.remote_key_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            DemoCard {
                StatusChip(
                    text = if (state.connected) {
                        stringResource(R.string.status_connected)
                    } else {
                        stringResource(R.string.status_disconnected)
                    }
                )
                KeyPad(
                    enabled = state.connected,
                    onSendKey = onSendKey,
                )
                RowOutlinedButton(
                    text = stringResource(R.string.button_back_to_main),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBackClick,
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.section_logs))
            LogPanel(logs = state.logs)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun KeyPad(
    enabled: Boolean,
    onSendKey: (GDBleKey) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            RowButton(
                text = stringResource(R.string.key_up),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.UP) },
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        ActionRow {
            RowButton(
                text = stringResource(R.string.key_left),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.LEFT) },
            )
            RowButton(
                text = stringResource(R.string.key_ok),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.CENTER) },
            )
            RowButton(
                text = stringResource(R.string.key_right),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.RIGHT) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            RowButton(
                text = stringResource(R.string.key_down),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.DOWN) },
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        ActionRow {
            RowOutlinedButton(
                text = stringResource(R.string.key_back),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.BACK) },
            )
            RowOutlinedButton(
                text = stringResource(R.string.key_home),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.HOME) },
            )
            RowOutlinedButton(
                text = stringResource(R.string.key_refresh),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSendKey(GDBleKey.REFRESH) },
            )
        }
    }
}
