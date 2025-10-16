package com.d10ng.bluetooth.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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

        // 右侧：通讯区域
        val messages = remember { mutableStateListOf<ChatMessage>() }

        // 连接后监听通知数据
        LaunchedEffect(connection) {
            connection ?: return@LaunchedEffect
            // 同步订阅状态
            scope.launch {
                connection!!.notifyStatusFlow.collect { list ->
                    subscribedChars.clear()
                    subscribedChars.addAll(list)
                }
            }
            // 通知数据
            scope.launch {
                connection!!.notifyDataFlow.collect { data ->
                    val content = runCatching { data.data.decodeToString() }.getOrDefault("")
                    messages.add(ChatMessage(Clock.System.now(), "RX", "${data.characteristic.uuid}: $content"))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            // 左侧连接区域
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp)
            ) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            Button(onClick = {
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
                            }) {
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
                                        // 发现服务
                                        services = runCatching { conn.discoverServices() }.getOrDefault(emptyList())
                                        // 默认选择首个可写特征
                                        selectedWriteChar = services.flatMap { it.characteristics }
                                            .firstOrNull { ch -> ch.properties.contains(BleGattCharacteristicProperty.WRITE) || ch.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) }
                                    }.onFailure { e ->
                                        // 连接失败提示为消息
                                        messages.add(ChatMessage(Clock.System.now(), "ERR", e.message ?: "连接失败"))
                                    }
                                    connecting = false
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
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)
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
                                conn.write(ch, msg.encodeToByteArray())
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
    services: List<BleGattService>,
    subscribed: List<BleGattCharacteristic>,
    onToggleNotify: (BleGattCharacteristic, Boolean) -> Unit,
    selectedWrite: BleGattCharacteristic?,
    onSelectWrite: (BleGattCharacteristic) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "设备", style = MaterialTheme.typography.titleMedium)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                            AssistChip(onClick = {}, label = { Text("已连接") })
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
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = service.uuid, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    service.characteristics.forEach { ch ->
                        Column(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text(text = ch.uuid, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ch.properties.forEach { prop ->
                                    AssistChip(onClick = {}, label = { Text(propLabel(prop)) }, modifier = Modifier, enabled = false)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val canNotify = ch.properties.contains(BleGattCharacteristicProperty.NOTIFY) || ch.properties.contains(BleGattCharacteristicProperty.INDICATE)
                                if (canNotify) {
                                    val enabled = subscribed.any { it.uuid == ch.uuid }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = enabled, onCheckedChange = { onToggleNotify(ch, it) })
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (enabled) "已订阅" else "订阅")
                                    }
                                }
                                val canWrite = ch.properties.contains(BleGattCharacteristicProperty.WRITE) || ch.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE)
                                if (canWrite) {
                                    FilledTonalButton(onClick = { onSelectWrite(ch) }) {
                                        Text(text = if (selectedWrite?.uuid == ch.uuid) "写入目标（当前）" else "选择为写入目标")
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChatArea(
    enabled: Boolean,
    title: String,
    messages: List<ChatMessage>,
    onClear: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val timeStr = msg.time.toHHmmssSSS()
    val bg = when (msg.dir) {
        "TX" -> MaterialTheme.colorScheme.primaryContainer
        "RX" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val fg = when (msg.dir) {
        "TX" -> MaterialTheme.colorScheme.onPrimaryContainer
        "RX" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.background(color = fg.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text(text = msg.dir) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = timeStr, style = MaterialTheme.typography.bodySmall, color = fg)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = msg.content, color = fg)
        }
    }
}