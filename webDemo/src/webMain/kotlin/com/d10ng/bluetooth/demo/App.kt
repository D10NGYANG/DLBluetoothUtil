package com.d10ng.bluetooth.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.d10ng.bluetooth.ABleConnection
import com.d10ng.bluetooth.BluetoothManagerLog
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.getPlatformBleManager
import com.d10ng.bluetooth.registerWebBleUseService
import com.d10ng.log.LogLevel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class ChatMessage (val time: Instant, val dir: String, val content: String)

@OptIn(ExperimentalTime::class, FormatStringsInDatetimeFormats::class)
fun Instant.toHHmmssSSS(): String {
    return this.toLocalDateTime(TimeZone.currentSystemDefault())
        .format(LocalDateTime.Format { byUnicodePattern("HH:mm:ss.SSS") })
}

@OptIn(ExperimentalTime::class)
@Composable
fun App() {
    MaterialTheme {
        // 日志级别
        LaunchedEffect(Unit) { BluetoothManagerLog.miniLevel = LogLevel.VERBOSE }

        val bleManager = remember { getPlatformBleManager() }
        val scope = rememberCoroutineScope()

        // 左侧：服务注册 + 扫描 + 设备信息 + 服务特征管理
        var serviceInput by remember { mutableStateOf("") }
        var scanning by remember { mutableStateOf(false) }
        var selectedDevice by remember { mutableStateOf<BleDevice?>(null) }
        var connecting by remember { mutableStateOf(false) }
        var connection by remember { mutableStateOf<ABleConnection?>(null) }
        var services by remember { mutableStateOf<List<BleGattService>>(emptyList()) }
        var selectedWriteChar by remember { mutableStateOf<BleGattCharacteristic?>(null) }
        val subscribedChars = remember { mutableStateListOf<BleGattCharacteristic>() }
        var maxWriteSize by remember { mutableStateOf(20) }

        // 右侧：通讯区域
        val messages = remember { mutableStateListOf<ChatMessage>() }

        // 连接后：发现服务、同步订阅状态、监听通知并默认选择可写特征
        LaunchedEffect(connection) {
            val conn = connection ?: return@LaunchedEffect
            // 发现服务（再次触发，避免首次连接时服务为空）
            services = runCatching { conn.discoverServices() }.getOrDefault(emptyList())
            maxWriteSize = max(20, runCatching { conn.requestMaxMtu() }.getOrDefault(20))
            // 默认选择首个可写特征
            selectedWriteChar = services.flatMap { it.characteristics }
                .firstOrNull { ch -> ch.properties.contains(BleGattCharacteristicProperty.WRITE) || ch.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) }

            // 自动订阅所有可通知的特征（不包含 INDICATE）
            services.flatMap { it.characteristics }
                .filter { ch -> ch.properties.contains(BleGattCharacteristicProperty.NOTIFY) }
                .forEach { ch ->
                    scope.launch { runCatching { conn.notify(ch, true) } }
                }

            // 同步订阅状态
            scope.launch {
                conn.notifyStatusFlow.collect { list ->
                    subscribedChars.clear()
                    subscribedChars.addAll(list)
                }
            }
            // 通知数据
            scope.launch {
                conn.notifyDataFlow.collect { data ->
                    val content = runCatching { data.data.decodeToString() }.getOrDefault("")
                    messages.add(ChatMessage(Clock.System.now(), "RX", "${data.characteristic.uuid}: $content"))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp)
        ) {
            // 左侧连接区域
            Surface(modifier = Modifier.weight(0.33f).fillMaxHeight().padding(end = 8.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "服务注册", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = serviceInput,
                            onValueChange = { serviceInput = it },
                            singleLine = true,
                            label = { Text("请输入服务UUID，逗号分隔") },
                            placeholder = { Text("如 battery_service,0000180F-0000-1000-8000-00805F9B34FB") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                enabled = connection == null,
                                onClick = {
                                    // 注册服务
                                    serviceInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { uuid ->
                                        registerWebBleUseService(uuid)
                                    }
                                    // requestDevice (scan)
                                    scanning = true
                                    selectedDevice = null
                                    scope.launch {
                                        bleManager.scan().collect { dev ->
                                            selectedDevice = dev
                                        }
                                        scanning = false
                                    }
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = if (scanning) "扫描中..." else "扫描设备")
                                }
                            }
                            if (scanning) {
                                Spacer(modifier = Modifier.width(12.dp))
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        HorizontalDivider()
                        DeviceInfoArea(
                            device = selectedDevice,
                            connecting = connecting,
                            connection = connection,
                            onConnect = { dev ->
                                if (dev == null || connection != null) return@DeviceInfoArea
                                connecting = true
                                scope.launch {
                                    runCatching {
                                        val conn = bleManager.connect(dev)
                                        connection = conn
                                    }.onFailure { e ->
                                        // 连接失败提示为消息
                                        messages.add(ChatMessage(Clock.System.now(), "ERR", e.message ?: "连接失败"))
                                    }
                                    connecting = false
                                }
                            },
                            onDisconnect = {
                                val conn = connection ?: return@DeviceInfoArea
                                scope.launch {
                                    runCatching { conn.disconnect() }
                                    connection = null
                                    services = emptyList()
                                    selectedWriteChar = null
                                    subscribedChars.clear()
                                }
                            },
                            services = services,
                            subscribed = subscribedChars,
                            onToggleNotify = { ch, enable ->
                                val conn = connection ?: return@DeviceInfoArea
                                scope.launch { runCatching { conn.notify(ch, enable) } }
                            },
                            selectedWrite = selectedWriteChar,
                            onSelectWrite = { ch -> selectedWriteChar = ch }
                        )
                    }
                }
            }

            // 右侧通讯区域
            Column(
                modifier = Modifier.weight(0.67f).fillMaxHeight().padding(start = 8.dp)
            ) {
                ChatArea(
                    enabled = connection != null,
                    title = selectedDevice?.name ?: "通讯",
                    messages = messages,
                    onClear = { messages.clear() },
                    onSend = { text ->
                        val conn = connection ?: return@ChatArea
                        val ch = selectedWriteChar ?: run {
                            messages.add(ChatMessage(Clock.System.now(), "ERR", "请先在左侧选择一个可写特征"))
                            return@ChatArea
                        }
                        val msg = if (text.isNotBlank()) text + "\r\n" else "\r\n"
                        scope.launch {
                            runCatching {
                                val data = msg.encodeToByteArray()
                                var idx = 0
                                while (idx < data.size) {
                                    val end = min(data.size, idx + maxWriteSize)
                                    val chunk = data.copyOfRange(idx, end)
                                    conn.write(ch, chunk)
                                    idx = end
                                }
                                messages.add(ChatMessage(Clock.System.now(), "TX", "${ch.uuid}: $text"))
                            }.onFailure { e ->
                                messages.add(ChatMessage(Clock.System.now(), "ERR", e.message ?: "发送失败"))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoArea(
    device: BleDevice?,
    connecting: Boolean,
    connection: ABleConnection?,
    onConnect: (BleDevice?) -> Unit,
    onDisconnect: () -> Unit,
    services: List<BleGattService>,
    subscribed: List<BleGattCharacteristic>,
    onToggleNotify: (BleGattCharacteristic, Boolean) -> Unit,
    selectedWrite: BleGattCharacteristic?,
    onSelectWrite: (BleGattCharacteristic) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "设备", style = MaterialTheme.typography.titleMedium)
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                if (device == null) {
                    Text(text = "尚未选择设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = device.name ?: "Unknown", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (connection == null) {
                            if (connecting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                FilledTonalButton(onClick = { onConnect(device) }) {
                                    Text("连接")
                                }
                            }
                        } else {
                            FilledTonalButton(onClick = { onDisconnect() }) {
                                Text("断开连接")
                            }
                        }
                    }
                }
            }
        }

        // 服务特征列表（连接成功后出现）
        if (connection != null && services.isNotEmpty()) {
            Text(text = "服务特征", style = MaterialTheme.typography.titleMedium)
            ServicesList(
                services = services,
                subscribed = subscribed,
                onToggleNotify = onToggleNotify,
                selectedWrite = selectedWrite,
                onSelectWrite = onSelectWrite
            )
        } else if (connection != null) {
            // 已连接但没有服务时给出提示
            EmptyServicesHint(true)
        }
    }
}

@Composable
private fun ServicesList(
    services: List<BleGattService>,
    subscribed: List<BleGattCharacteristic>,
    onToggleNotify: (BleGattCharacteristic, Boolean) -> Unit,
    selectedWrite: BleGattCharacteristic?,
    onSelectWrite: (BleGattCharacteristic) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        services.forEach { service ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "服务：" + service.uuid,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    service.characteristics.forEachIndexed { cIdx, ch ->
                        val props = ch.properties
                        val canNotify = props.contains(BleGattCharacteristicProperty.NOTIFY)
                        val enabled = subscribed.any { it.uuid == ch.uuid }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "特征：" + ch.uuid, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ch.properties.forEach { p ->
                                        val (bg, fg) = propColors(p)
                                        val shape = RoundedCornerShape(8.dp)
                                        if (p == BleGattCharacteristicProperty.WRITE || p == BleGattCharacteristicProperty.WRITE_NO_RESPONSE) {
                                            val selected = selectedWrite?.uuid == ch.uuid
                                            Box(
                                                modifier = Modifier
                                                    .background(bg, shape)
                                                    .clickable { onSelectWrite(ch) }
                                            ) {
                                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    if (selected) {
                                                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(text = propLabel(p), color = fg, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        } else {
                                            Surface(color = bg, shape = shape) {
                                                Text(text = propLabel(p), color = fg, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            if (canNotify) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = enabled, onCheckedChange = { onToggleNotify(ch, it) })
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (enabled) "已订阅" else "订阅")
                                }
                            }
                        }
                        if (cIdx < service.characteristics.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyServicesHint(visible: Boolean) {
    if (!visible) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(text = "服务特征", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "未发现服务或未注册访问的服务。请在上方输入要访问的服务UUID（可逗号分隔），点击‘扫描设备’后再连接。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun propLabel(prop: BleGattCharacteristicProperty): String = when (prop) {
    BleGattCharacteristicProperty.BROADCAST -> "BROADCAST"
    BleGattCharacteristicProperty.READ -> "READ"
    BleGattCharacteristicProperty.WRITE_NO_RESPONSE -> "WRITE_NR"
    BleGattCharacteristicProperty.WRITE -> "WRITE"
    BleGattCharacteristicProperty.NOTIFY -> "NOTIFY"
    BleGattCharacteristicProperty.INDICATE -> "INDICATE"
    BleGattCharacteristicProperty.SIGNED_WRITE -> "SIGNED_WRITE"
    BleGattCharacteristicProperty.EXTENDED_PROPS -> "EXTENDED"
}

@Composable
private fun propColors(prop: BleGattCharacteristicProperty): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (prop) {
        BleGattCharacteristicProperty.READ -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BleGattCharacteristicProperty.WRITE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BleGattCharacteristicProperty.WRITE_NO_RESPONSE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) to MaterialTheme.colorScheme.onPrimaryContainer
        BleGattCharacteristicProperty.NOTIFY -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BleGattCharacteristicProperty.INDICATE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f) to MaterialTheme.colorScheme.onTertiaryContainer
        BleGattCharacteristicProperty.BROADCAST -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        BleGattCharacteristicProperty.SIGNED_WRITE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f) to MaterialTheme.colorScheme.onSurfaceVariant
        BleGattCharacteristicProperty.EXTENDED_PROPS -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatArea(
    enabled: Boolean,
    title: String,
    messages: List<ChatMessage>,
    onClear: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(messages.size) {
        val last = max(messages.size - 1, 0)
        listState.scrollToItem(last)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = title.ifBlank { "通讯" }) },
            actions = {
                IconButton(onClick = onClear, enabled = enabled) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "清空记录")
                }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState
        ) {
            items(messages.size) { idx ->
                val m = messages[idx]
                MessageBubble(msg = m)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                label = { Text("发送内容") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { if (enabled) onSend(input) }, enabled = enabled) {
                Text("发送")
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isTx = remember(msg.dir) { msg.dir == "TX" }
    val container = if (isTx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val onContainer = if (isTx) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    val borderColor = if (isTx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val metaPayload = remember(msg.content) {
        val parts = msg.content.split(": ", limit = 2)
        val meta = parts.getOrNull(0) ?: ""
        val body = (parts.getOrNull(1) ?: msg.content).trim()
        meta to body
    }
    val meta = metaPayload.first
    val payload = metaPayload.second
    val timeText = remember(msg.time) { msg.time.toHHmmssSSS() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "特征：$meta",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        val bubbleShape = RoundedCornerShape(bottomEnd = 8.dp, bottomStart = 8.dp)
        Surface(
            color = container,
            tonalElevation = 2.dp,
            shape = bubbleShape,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = borderColor, shape = bubbleShape)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                SelectionContainer {
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}