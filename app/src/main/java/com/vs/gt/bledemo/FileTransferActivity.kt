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
import androidx.compose.foundation.lazy.items
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
import com.goolton.ble.GDBleListener
import com.goolton.ble.GDBleSdk
import com.goolton.ble.protocol.Action
import com.goolton.ble.protocol.BleFile
import com.goolton.ble.protocol.BleMsg
import com.goolton.ble.protocol.CMD
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TEST_PKG = "com.goolton.teleprompter"

class FileTransferActivity : ComponentActivity() {

    private var uiState by mutableStateOf(FileTransferUiState())

    /**
     * 文件传输页面关注 VIEW_FILE、DOWNLOAD_FILE、ADD_FILE 和 FILE_END。
     *
     * SDK 已经把底层分片、缓存文件保存等逻辑封装好，客户侧只需要根据协议消息更新 UI。
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
                handleFileMessage(message)
            }
        }

        override fun onRawMessageReceived(json: String) {
            runOnUiThread {
                appendLog(getString(R.string.log_raw, json))
            }
        }

        override fun onFileReceived(absolutePath: String) {
            runOnUiThread {
                uiState = uiState.copy(latestDownloadPath = absolutePath)
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

        // MainActivity 已经初始化过 SDK；这里再次调用 init 也是安全的，方便客户复制单页示例。
        GDBleSdk.init(applicationContext)
        GDBleSdk.addListener(sdkListener)
        uiState = uiState.copy(connected = GDBleSdk.isConnected())
        appendLog(getString(R.string.log_file_transfer_page_opened))

        setContent {
            GTBleDemoTheme {
                FileTransferScreen(
                    state = uiState,
                    testPkg = TEST_PKG,
                    onBackClick = ::finish,
                    onQueryFileList = ::queryFileList,
                    onUploadTestFile = ::uploadTestFile,
                    onDownloadFile = ::downloadFile,
                )
            }
        }
    }

    override fun onDestroy() {
        GDBleSdk.removeListener(sdkListener)
        super.onDestroy()
    }

    private fun handleFileMessage(message: BleMsg) {
        if (message.pkg != null && message.pkg != TEST_PKG) {
            return
        }
        when {
            message.cmd == CMD.ERROR -> {
                appendLog(getString(R.string.log_error, message.action))
            }

            message.action == Action.VIEW_FILE -> {
                val files = message.data?.fileList.orEmpty()
                uiState = uiState.copy(files = files)
                appendLog(getString(R.string.log_file_transfer_list_updated, files.size))
            }

            message.action == Action.DOWNLOAD_FILE -> {
                val name = message.data?.file?.name ?: message.action.name
                appendLog(getString(R.string.log_file_transfer_download_ack, name))
            }

            message.action == Action.ADD_FILE -> {
                appendLog(getString(R.string.log_file_transfer_upload_ack))
            }

            message.cmd == CMD.FILE_END -> {
                appendLog(getString(R.string.log_file_transfer_file_end))
            }
        }
    }

    private fun queryFileList() {
        if (!ensureConnected()) return
        appendLog(getString(R.string.log_file_transfer_request_list, TEST_PKG))
        GDBleSdk.viewFile(TEST_PKG)
    }

    private fun downloadFile(file: BleFile) {
        if (!ensureConnected()) return
        appendLog(getString(R.string.log_file_transfer_download_request, file.name, file.id))
        GDBleSdk.downloadFile(TEST_PKG, file.id)
    }

    private fun uploadTestFile() {
        if (!ensureConnected()) return
        val file = createTestTeleprompterFile()
        uiState = uiState.copy(latestUploadPath = file.absolutePath)
        appendLog(getString(R.string.log_file_transfer_upload_created, file.absolutePath))
        appendLog(getString(R.string.log_file_transfer_upload_start, file.name))
        GDBleSdk.sendFile(file, TEST_PKG)
    }

    private fun createTestTeleprompterFile(): File {
        val timestamp = System.currentTimeMillis()
        val title = getString(R.string.upload_test_file_title, timestamp)
        val content = getString(R.string.upload_test_file_content)
        val dir = File(cacheDir, TEST_PKG).apply { mkdirs() }
        val file = File(dir, "$title.txt")

        // 这里故意随机生成一个简单 txt 文件，模拟客户 App 将提词器文本写入本地后交给 SDK 上传。
        OutputStreamWriter(file.outputStream(), Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
        return file
    }

    private fun ensureConnected(): Boolean {
        if (GDBleSdk.isConnected()) {
            return true
        }
        appendLog(getString(R.string.log_file_transfer_skip_not_connected))
        uiState = uiState.copy(connected = false)
        return false
    }

    private fun appendLog(message: String) {
        uiState = uiState.copy(logs = (listOf(demoLogLine(message)) + uiState.logs).take(100))
    }

    private fun localizedBoolean(value: Boolean): String {
        return getString(if (value) R.string.boolean_true else R.string.boolean_false)
    }
}

@Composable
private fun FileTransferScreen(
    state: FileTransferUiState,
    testPkg: String,
    onBackClick: () -> Unit,
    onQueryFileList: () -> Unit,
    onUploadTestFile: () -> Unit,
    onDownloadFile: (BleFile) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.file_transfer_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.file_transfer_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            FileTransferActionPanel(
                connected = state.connected,
                testPkg = testPkg,
                onBackClick = onBackClick,
                onQueryFileList = onQueryFileList,
                onUploadTestFile = onUploadTestFile,
            )
        }

        item {
            SectionTitle(stringResource(R.string.section_transfer_result))
            TransferResultPanel(state = state)
        }

        item {
            SectionTitle(stringResource(R.string.section_file_list))
        }

        if (state.files.isEmpty()) {
            item {
                DemoCard {
                    Text(
                        text = stringResource(R.string.empty_file_list),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.files, key = { it.id }) { file ->
                FileItemRow(
                    file = file,
                    onDownloadFile = { onDownloadFile(file) },
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
private fun FileTransferActionPanel(
    connected: Boolean,
    testPkg: String,
    onBackClick: () -> Unit,
    onQueryFileList: () -> Unit,
    onUploadTestFile: () -> Unit,
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
        Text(
            text = stringResource(R.string.file_transfer_test_pkg, testPkg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionRow {
            RowButton(
                text = stringResource(R.string.button_query_file_list),
                modifier = Modifier.weight(1f),
                enabled = connected,
                onClick = onQueryFileList,
            )
            RowButton(
                text = stringResource(R.string.button_upload_test_file),
                modifier = Modifier.weight(1f),
                enabled = connected,
                onClick = onUploadTestFile,
            )
        }
        RowOutlinedButton(
            text = stringResource(R.string.button_back_to_main),
            modifier = Modifier.fillMaxWidth(),
            onClick = onBackClick,
        )
    }
}

@Composable
private fun TransferResultPanel(state: FileTransferUiState) {
    DemoCard {
        Text(
            text = stringResource(
                R.string.file_transfer_latest_upload,
                state.latestUploadPath ?: stringResource(R.string.file_transfer_none),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.file_transfer_latest_download,
                state.latestDownloadPath ?: stringResource(R.string.file_transfer_none),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FileItemRow(
    file: BleFile,
    onDownloadFile: () -> Unit,
) {
    DemoCard {
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.file_item_id, file.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.file_item_size, file.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.file_item_time, formatFileTime(file.time)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowButton(
            text = stringResource(R.string.button_download),
            modifier = Modifier.fillMaxWidth(),
            onClick = onDownloadFile,
        )
    }
}

private fun formatFileTime(time: Long): String {
    if (time <= 0L) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
}
