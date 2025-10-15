package com.d10ng.bluetooth.demo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.d10ng.bluetooth.ABleConnection
import com.d10ng.bluetooth.BluetoothManagerLog
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.getPlatformBleManager
import com.d10ng.log.LogLevel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

sealed class Screen {
    data object DeviceList : Screen()
    data class Chat(val connection: ABleConnection) : Screen()
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.DeviceList) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            BluetoothManagerLog.miniLevel = LogLevel.VERBOSE
        }

        when (val s = screen) {
            is Screen.DeviceList -> DeviceListScreen(
                onConnected = { conn -> screen = Screen.Chat(conn) }
            )
            is Screen.Chat -> ChatScreen(
                connection = s.connection,
                onBack = {
                    // 返回时自动断开连接
                    scope.launch { s.connection.disconnect() }
                    screen = Screen.DeviceList
                }
            )
        }
    }
}

@Composable
private fun DeviceListScreen(
    onConnected: (ABleConnection) -> Unit
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceListTopBar(scanning: Boolean, onScanClick: () -> Unit) {
        // 无限旋转动画（仅在扫描时应用到图标）
        val infinite = rememberInfiniteTransition()
        val angle by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000, easing = LinearEasing)),
            label = "scanRotation"
        )
        TopAppBar(
            title = { Text("设备列表") },
            actions = {
                IconButton(onClick = onScanClick) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.rotate(if (scanning) angle else 0f)
                    )
                }
            }
        )
    }
    val bleManager = remember { getPlatformBleManager() }
    val scope = rememberCoroutineScope()

    var scanning by remember { mutableStateOf(false) }
    val devices = remember { mutableStateListOf<BleDevice>() }
    var connectProgress by remember { mutableStateOf<BleDevice?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 扫描并实时更新设备列表
    LaunchedEffect(scanning) {
        if (scanning) {
            // 如果支持开启蓝牙，尝试开启
            runCatching {
                if (bleManager.isSupportEnable()) {
                    bleManager.enable()
                }
            }
            bleManager.scan().collect { dev ->
                val validName = !dev.name.isNullOrBlank() && dev.name != "Unknown"
                if (validName && devices.none { it.address == dev.address }) devices.add(dev)
            }
        }
    }

    Scaffold(
        topBar = {
            DeviceListTopBar(
                scanning = scanning,
                onScanClick = {
                    if (scanning) {
                        // 停止扫描
                        scanning = false
                    } else {
                        // 开始扫描并清空旧列表
                        devices.clear()
                        errorMsg = null
                        scanning = true
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (errorMsg != null) {
                Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            // 扫描状态提示
            val infiniteChip = rememberInfiniteTransition()
            val chipAngle by infiniteChip.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000, easing = LinearEasing)),
                label = "scanRotationChip"
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { },
                    label = { Text(if (scanning) "扫描中..." else "点击右上角刷新扫描设备") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).rotate(if (scanning) chipAngle else 0f),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices.size) { idx ->
                    val dev = devices[idx]
                    DeviceItem(
                        device = dev,
                        connecting = dev.address == connectProgress?.address,
                        onClick = {
                            connectProgress = dev
                            errorMsg = null
                            scope.launch {
                                runCatching {
                                    val conn = bleManager.connect(dev)
                                    onConnected(conn)
                                }.onFailure { e ->
                                    errorMsg = e.message
                                }.also {
                                    connectProgress = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: BleDevice,
    connecting: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    SignalIndicator(rssi = device.rssi)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                FilledTonalButton(onClick = onClick) {
                    Text("连接")
                }
            }
        }
    }
}

@Composable
private fun SignalIndicator(rssi: Int) {
    val level = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -85 -> 1
        else -> 0
    }
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val heights = listOf(6.dp, 10.dp, 14.dp, 18.dp)
    Row(verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { idx, h ->
            val color = if (idx < level) active else inactive
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h)
                    .background(color)
            )
            if (idx != heights.lastIndex) Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    connection: ABleConnection,
    onBack: () -> Unit
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatTopBar(
        notifyStatusList: List<BleGattCharacteristic>,
        onOpenServiceDialog: () -> Unit,
        onClearLog: () -> Unit
    ) {
        TopAppBar(
            title = { Text(text = connection.device.name ?: "Unknown") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                // 服务特征管理：图标 + 数字徽标，点击打开统一弹窗
                Box {
                    BadgedBox(badge = { Badge { Text("${notifyStatusList.size}") } }) {
                        IconButton(onClick = onOpenServiceDialog) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = "服务特征管理")
                        }
                    }
                }
                // 清除按钮，位于订阅管理按钮右侧
                IconButton(onClick = onClearLog) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "清空记录")
                }
            }
        )
    }
    val scope = rememberCoroutineScope()

    var services by remember { mutableStateOf<List<BleGattService>>(emptyList()) }
    var selectedChar by remember { mutableStateOf<BleGattCharacteristic?>(null) }
    var input by remember { mutableStateOf("") }

    // 订阅状态
    val notifyStatus by connection.notifyStatusFlow.collectAsState(initial = emptyList())

    // 消息记录（最新消息置底）
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // 进入页面时发现服务并自动订阅所有可通知特征
    LaunchedEffect(connection) {
        runCatching { connection.requestMaxMtu() }
        services = runCatching { connection.discoverServices() }.getOrDefault(emptyList())
        val notifiables = services.flatMap { it.characteristics }
            .filter { it.properties.contains(BleGattCharacteristicProperty.NOTIFY) || it.properties.contains(BleGattCharacteristicProperty.INDICATE) }
        notifiables.forEach { ch ->
            runCatching { connection.notify(ch, true) }
        }
        // 默认选择第一个可写特征
        val writables = services.flatMap { it.characteristics }
            .filter { it.properties.contains(BleGattCharacteristicProperty.WRITE) || it.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) }
        selectedChar = writables.firstOrNull()
    }

    // 收到通知时追加消息并自动滚动到底部
    LaunchedEffect(connection) {
        connection.notifyDataFlow.collect { data ->
            val content = runCatching { data.data.decodeToString() }.getOrDefault("")
            messages.add(ChatMessage(Clock.System.now(), "RX", "${data.characteristic.uuid}: $content"))
            // 自动滚动至最新消息
            scope.launch { listState.animateScrollToItem(maxOf(messages.size - 1, 0)) }
        }
    }

    var serviceDialogExpanded by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        ChatTopBar(
            notifyStatusList = notifyStatus,
            onOpenServiceDialog = { serviceDialogExpanded = true },
            onClearLog = { messages.clear() }
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 订阅管理已迁移至标题栏动作，页面不再占位

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(count = messages.size, key = { i ->
                    val m = messages[i]
                    "${m.time}-${m.dir}-${m.content.length}"
                }) { idx ->
                    val m = messages[idx]
                    MessageBubble(msg = m)
                }
            }

            // 统一的服务特征列表弹窗
            if (serviceDialogExpanded) {
                Dialog(
                    onDismissRequest = { serviceDialogExpanded = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = "服务特征列表",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // 属性颜色与标签
                                fun propLabel(prop: BleGattCharacteristicProperty): String = when (prop) {
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
                                fun propColors(prop: BleGattCharacteristicProperty): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
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

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(count = services.size, key = { i -> services[i].uuid.toString() }) { sIdx ->
                                        val service = services[sIdx]
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                                Text(
                                                    text = "服务：" + service.uuid.toString(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                service.characteristics.forEachIndexed { cIdx, ch ->
                                                    val props = ch.properties
                                                    val isNotifiable = props.contains(BleGattCharacteristicProperty.NOTIFY)
                                                    val subscribed = notifyStatus.any { it.uuid == ch.uuid }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "特征：" + ch.uuid.toString(),
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                props.forEach { p ->
                                                                    val (bg, fg) = propColors(p)
                                                                    val shape = RoundedCornerShape(8.dp)
                                                                    if (p == BleGattCharacteristicProperty.WRITE || p == BleGattCharacteristicProperty.WRITE_NO_RESPONSE) {
                                                                        // 可写特征：点击选择为写入特征（单选）
                                                                        val selected = selectedChar?.uuid == ch.uuid
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .background(bg, shape)
                                                                                .clickable { selectedChar = ch }
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
                                                        if (isNotifiable) {
                                                            Switch(
                                                                checked = subscribed,
                                                                onCheckedChange = { enable ->
                                                                    scope.launch { runCatching { connection.notify(ch, enable) } }
                                                                }
                                                            )
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

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { serviceDialogExpanded = false }) { Text("关闭") }
                                }
                            }
                        }
                    }
                }
            }

            // 底部输入与发送（紧凑排布）
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入文本") }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val ch = selectedChar ?: return@Button
                        val text = input + "\r\n"
                        val bytes = text.encodeToByteArray()
                        scope.launch {
                            runCatching { connection.write(ch, bytes) }
                                .onSuccess {
                                    messages.add(ChatMessage(Clock.System.now(), "TX", "${ch.uuid}: ${text.trim()}"))
                                    input = ""
                                    listState.animateScrollToItem(maxOf(messages.size - 1, 0))
                                }
                        }
                    },
                    enabled = selectedChar != null
                ) {
                    Text("发送")
                }
            }
        }
    }
}
@OptIn(ExperimentalTime::class)
data class ChatMessage (val time: Instant, val dir: String, val content: String)

@OptIn(ExperimentalTime::class, FormatStringsInDatetimeFormats::class)
fun Instant.toHHmmssSSS(): String {
    return this.toLocalDateTime(TimeZone.currentSystemDefault())
        .format(LocalDateTime.Format { byUnicodePattern("HH:mm:ss.SSS") })
}

@OptIn(ExperimentalTime::class)
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isTx = remember(msg.dir) { msg.dir == "TX" }
    val container = if (isTx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (isTx) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
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
        // 时间（置于文本框外）
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        // 特征（置于文本框外）
        Text(
            text = "特征：$meta",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 文本（圆角背景，区分 RX/TX）
        Surface(color = container, tonalElevation = 2.dp, shape = RoundedCornerShape(bottomEnd = 8.dp, bottomStart = 8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                SelectionContainer {
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}