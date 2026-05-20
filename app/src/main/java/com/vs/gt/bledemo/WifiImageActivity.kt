package com.vs.gt.bledemo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.annotations.SerializedName
import com.goolton.ble.GDBleListener
import com.goolton.ble.GDBleSdk
import com.goolton.ble.protocol.Action
import com.goolton.ble.protocol.BleFile
import com.goolton.ble.protocol.BleMsg
import com.goolton.ble.protocol.CMD
import com.goolton.ble.protocol.GsonHolder
import com.goolton.ble.protocol.NetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MEDIA_TYPE_IMAGE = "image"
private const val MEDIA_PAGE_SIZE = 100

class WifiImageActivity : ComponentActivity() {

    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(activityJob + Dispatchers.Main.immediate)
    private var healthCheckJob: Job? = null
    private var uiState by mutableStateOf(WifiImageUiState())

    /**
     * Wi-Fi 图片页同时使用 BLE 和局域网 HTTP。
     *
     * BLE 负责开启眼镜端 Wi-Fi service、返回服务地址、查询图片列表；
     * 图片二进制数据通过 HTTP 加载，路径和 PhoneAssistant 相册模块保持一致。
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
                handleWifiImageMessage(message)
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

        GDBleSdk.init(applicationContext)
        GDBleSdk.addListener(sdkListener)
        uiState = uiState.copy(
            connected = GDBleSdk.isConnected(),
            localIp = findLocalIpv4Address(),
        )
        appendLog(getString(R.string.log_wifi_image_page_opened))

        setContent {
            GTBleDemoTheme {
                WifiImageScreen(
                    state = uiState,
                    onBackClick = ::finish,
                    onStartServiceClick = ::startWifiService,
                    onStopServiceClick = ::stopWifiService,
                    onImageClick = ::selectImage,
                )
            }
        }
    }

    override fun onDestroy() {
        healthCheckJob?.cancel()
        activityJob.cancel()
        if (uiState.serviceRunning || uiState.checkingService || uiState.baseUrl != null) {
            GDBleSdk.stopWifiService()
        }
        GDBleSdk.removeListener(sdkListener)
        super.onDestroy()
    }

    private fun startWifiService() {
        if (!GDBleSdk.isConnected()) {
            appendLog(getString(R.string.log_wifi_image_skip_not_connected))
            uiState = uiState.copy(connected = false)
            return
        }
        healthCheckJob?.cancel()
        uiState = uiState.copy(
            netConfig = null,
            baseUrl = null,
            serviceRunning = false,
            checkingService = true,
            loadingImages = false,
            page = 0,
            total = 0,
            images = emptyList(),
            selectedImage = null,
            selectedRawUrl = null,
            localIp = findLocalIpv4Address(),
        )
        appendLog(getString(R.string.log_wifi_image_start_service))
        GDBleSdk.startWifiService()
    }

    private fun stopWifiService() {
        healthCheckJob?.cancel()
        appendLog(getString(R.string.log_wifi_image_stop_service))
        GDBleSdk.stopWifiService()
        uiState = uiState.copy(
            netConfig = null,
            baseUrl = null,
            serviceRunning = false,
            checkingService = false,
            loadingImages = false,
            page = 0,
            total = 0,
            images = emptyList(),
            selectedImage = null,
            selectedRawUrl = null,
        )
    }

    private fun handleWifiImageMessage(message: BleMsg) {
        when {
            message.cmd == CMD.ERROR -> {
                uiState = uiState.copy(checkingService = false, loadingImages = false)
                appendLog(getString(R.string.log_wifi_image_protocol_error, message.action))
            }

            message.action == Action.WIFI_SERVICE_START -> {
                val netConfig = parseNetConfig(message)
                if (netConfig == null || netConfig.ip.isBlank()) {
                    uiState = uiState.copy(checkingService = false)
                    appendLog(getString(R.string.log_wifi_image_empty_net_config))
                    return
                }

                val baseUrl = "http://${netConfig.ip}:${netConfig.port}"
                uiState = uiState.copy(
                    netConfig = netConfig,
                    baseUrl = baseUrl,
                    checkingService = true,
                    serviceRunning = false,
                )
                appendLog(getString(R.string.log_wifi_image_service_config, baseUrl))
                waitForWifiService(netConfig)
            }

            message.action == Action.WIFI_SERVICE_STOP -> {
                appendLog(getString(R.string.log_wifi_image_service_stopped))
            }

            message.action == Action.VIEW_MEDIA -> {
                handleViewMediaMessage(message)
            }
        }
    }

    private fun waitForWifiService(netConfig: NetConfig) {
        healthCheckJob?.cancel()
        healthCheckJob = activityScope.launch {
            val available = withContext(Dispatchers.IO) {
                waitServerAvailable(netConfig.ip, netConfig.port)
            }
            if (available) {
                uiState = uiState.copy(serviceRunning = true, checkingService = false)
                appendLog(getString(R.string.log_wifi_image_health_ok))
                requestImagePage(1, reset = true)
            } else {
                uiState = uiState.copy(serviceRunning = false, checkingService = false)
                appendLog(getString(R.string.log_wifi_image_health_failed))
            }
        }
    }

    private fun requestImagePage(page: Int, reset: Boolean) {
        if (!GDBleSdk.isConnected()) {
            appendLog(getString(R.string.log_wifi_image_skip_not_connected))
            uiState = uiState.copy(connected = false, loadingImages = false)
            return
        }
        uiState = if (reset) {
            uiState.copy(
                loadingImages = true,
                page = 0,
                total = 0,
                images = emptyList(),
                selectedImage = null,
                selectedRawUrl = null,
            )
        } else {
            uiState.copy(loadingImages = true)
        }
        appendLog(getString(R.string.log_wifi_image_request_page, page))
        GDBleSdk.viewMedia(MEDIA_TYPE_IMAGE, page, MEDIA_PAGE_SIZE)
    }

    private fun handleViewMediaMessage(message: BleMsg) {
        val pageInfo = parseMediaPageInfo(message)
        val newImages = message.data?.fileList.orEmpty()
            .filter { file -> file.type.isBlank() || file.type == MEDIA_TYPE_IMAGE }
        val mergedImages = (uiState.images + newImages)
            .distinctBy { file -> file.id }
            .sortedByDescending { file -> file.time }

        uiState = uiState.copy(
            loadingImages = false,
            page = pageInfo?.page ?: uiState.page,
            total = pageInfo?.total ?: mergedImages.size,
            images = mergedImages,
        )
        appendLog(getString(R.string.log_wifi_image_list_updated, mergedImages.size))

        if (pageInfo?.hasNext == true) {
            requestImagePage(pageInfo.page + 1, reset = false)
        }
    }

    private fun selectImage(file: BleFile) {
        val baseUrl = uiState.baseUrl
        if (baseUrl.isNullOrBlank()) {
            appendLog(getString(R.string.log_wifi_image_missing_base_url))
            return
        }
        val rawUrl = "$baseUrl/raw/image/${file.id}"
        uiState = uiState.copy(selectedImage = file, selectedRawUrl = rawUrl)
        appendLog(getString(R.string.log_wifi_image_select_raw, rawUrl))
    }

    private fun parseNetConfig(message: BleMsg): NetConfig? {
        return try {
            message.data?.data?.let { raw ->
                GsonHolder.gson.fromJson(raw, NetConfig::class.java)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseMediaPageInfo(message: BleMsg): MediaPageInfo? {
        return try {
            message.data?.data?.let { raw ->
                GsonHolder.gson.fromJson(raw, MediaPageInfo::class.java)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun appendLog(message: String) {
        uiState = uiState.copy(logs = (listOf(demoLogLine(message)) + uiState.logs).take(120))
    }

    private fun localizedBoolean(value: Boolean): String {
        return getString(if (value) R.string.boolean_true else R.string.boolean_false)
    }
}

@Composable
private fun WifiImageScreen(
    state: WifiImageUiState,
    onBackClick: () -> Unit,
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit,
    onImageClick: (BleFile) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.wifi_image_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.wifi_image_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            WifiImageActionPanel(
                state = state,
                onBackClick = onBackClick,
                onStartServiceClick = onStartServiceClick,
                onStopServiceClick = onStopServiceClick,
            )
        }

        item {
            SectionTitle(stringResource(R.string.section_original_image))
            OriginalImagePanel(state = state)
        }

        item {
            SectionTitle(stringResource(R.string.section_wifi_image_list))
        }

        if (state.loadingImages && state.images.isEmpty()) {
            item {
                DemoCard {
                    Text(
                        text = stringResource(R.string.wifi_image_loading_list),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (state.images.isEmpty()) {
            item {
                DemoCard {
                    Text(
                        text = stringResource(R.string.wifi_image_empty_list),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.images, key = { it.id }) { file ->
                WifiImageItemRow(
                    file = file,
                    baseUrl = state.baseUrl,
                    selected = state.selectedImage?.id == file.id,
                    onImageClick = { onImageClick(file) },
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
private fun WifiImageActionPanel(
    state: WifiImageUiState,
    onBackClick: () -> Unit,
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit,
) {
    DemoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                text = if (state.connected) {
                    stringResource(R.string.status_connected)
                } else {
                    stringResource(R.string.status_disconnected)
                }
            )
            StatusChip(
                text = when {
                    state.serviceRunning -> stringResource(R.string.status_wifi_service_running)
                    state.checkingService -> stringResource(R.string.status_wifi_service_checking)
                    else -> stringResource(R.string.status_wifi_service_stopped)
                }
            )
        }
        Text(
            text = stringResource(
                R.string.wifi_image_local_ip,
                state.localIp ?: stringResource(R.string.wifi_image_ip_unknown),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.wifi_image_same_network_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.wifi_image_service_url,
                state.baseUrl ?: stringResource(R.string.wifi_image_service_url_none),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionRow {
            RowButton(
                text = stringResource(R.string.button_start_wifi_service),
                modifier = Modifier.weight(1f),
                enabled = state.connected && !state.checkingService,
                onClick = onStartServiceClick,
            )
            RowOutlinedButton(
                text = stringResource(R.string.button_stop_wifi_service),
                modifier = Modifier.weight(1f),
                enabled = state.connected && (state.serviceRunning || state.checkingService),
                onClick = onStopServiceClick,
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
private fun OriginalImagePanel(state: WifiImageUiState) {
    DemoCard {
        val rawUrl = state.selectedRawUrl
        if (rawUrl == null) {
            Text(
                text = stringResource(R.string.wifi_image_original_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DemoCard
        }
        Text(
            text = state.selectedImage?.name ?: stringResource(R.string.wifi_image_unnamed),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        AsyncImage(
            model = rawUrl,
            contentDescription = state.selectedImage?.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = rawUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WifiImageItemRow(
    file: BleFile,
    baseUrl: String?,
    selected: Boolean,
    onImageClick: () -> Unit,
) {
    DemoCard(
        modifier = Modifier.clickable(onClick = onImageClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbUrl = baseUrl?.let { "$it/thumb/image/${file.id}" }
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbUrl == null) {
                    Text(
                        text = stringResource(R.string.wifi_image_thumb_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = file.name.ifBlank { stringResource(R.string.wifi_image_unnamed) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.file_item_id, file.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.file_item_size, file.size64),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.file_item_time, formatWifiImageTime(file.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.wifi_image_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private data class MediaPageInfo(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    @SerializedName("mediaType") val mediaType: String? = null,
)

private suspend fun waitServerAvailable(ip: String, port: Int, timeoutMs: Long = 3_000L): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    var delayMs = 120L
    while (System.currentTimeMillis() < deadline) {
        if (checkHealth(ip, port)) return true
        delay(delayMs)
        delayMs = (delayMs * 1.5f).toLong().coerceAtMost(350L)
    }
    return false
}

private fun checkHealth(ip: String, port: Int): Boolean {
    var connection: HttpURLConnection? = null
    return try {
        connection = URL("http://$ip:$port/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 350
        connection.readTimeout = 350
        connection.useCaches = false
        connection.responseCode == HttpURLConnection.HTTP_OK
    } catch (_: Throwable) {
        false
    } finally {
        connection?.disconnect()
    }
}

private fun Context.findLocalIpv4Address(): String? {
    return try {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val preferWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { networkInterface ->
                networkInterface.isUp && !networkInterface.isLoopback
            }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filterNot { address -> address.isLoopbackAddress }
                    .map { address -> networkInterface.name to address.hostAddress }
            }
            .sortedByDescending { (name, _) ->
                if (preferWifi && name.startsWith("wlan")) 1 else 0
            }
            .map { (_, address) -> address }
            .firstOrNull()
    } catch (_: Throwable) {
        null
    }
}

private fun formatWifiImageTime(time: Long): String {
    if (time <= 0L) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
}
