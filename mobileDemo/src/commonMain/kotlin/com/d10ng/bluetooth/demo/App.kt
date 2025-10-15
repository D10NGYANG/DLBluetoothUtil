package com.d10ng.bluetooth.demo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.d10ng.bluetooth.ABleConnection
import com.d10ng.bluetooth.BluetoothManagerLog
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.getPlatformBleManager
import com.d10ng.log.LogLevel
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
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
        notifiableList: List<BleGattCharacteristic>,
        onToggleNotify: (BleGattCharacteristic, Boolean) -> Unit
    ) {
        var subsMenuExpanded by remember { mutableStateOf(false) }
        TopAppBar(
            title = { Text(text = connection.device.name ?: "Unknown") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                // 订阅管理：图标 + 数字徽标，点击展开列表
                Box {
                    BadgedBox(badge = { Badge { Text("${notifyStatusList.size}") } }) {
                        IconButton(onClick = { subsMenuExpanded = true }) {
                            Icon(imageVector = Icons.Outlined.Notifications, contentDescription = "订阅管理")
                        }
                    }
                    DropdownMenu(expanded = subsMenuExpanded, onDismissRequest = { subsMenuExpanded = false }) {
                        notifiableList.forEachIndexed { idx, ch: BleGattCharacteristic ->
                            val subscribed = notifyStatusList.any { it.uuid == ch.uuid }
                            DropdownMenuItem(
                                onClick = {},
                                text = {
                                    Column {
                                        // 特征UUID：深色粗体
                                        Text(
                                            text = "特征：" + ch.uuid.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        // 服务UUID：浅色小字号
                                        Text(
                                            text = "服务：" + ch.serviceUuid.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = subscribed,
                                        onCheckedChange = { enable ->
                                            onToggleNotify(ch, enable)
                                        }
                                    )
                                }
                            )
                            if (idx < notifiableList.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
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
            messages.add(ChatMessage(Clock.System.now().toEpochMilliseconds(), "RX", "${data.characteristic.uuid}: $content"))
            // 自动滚动至最新消息
            scope.launch { listState.animateScrollToItem(maxOf(messages.size - 1, 0)) }
        }
    }

    val writables = services.flatMap { it.characteristics }
        .filter { it.properties.contains(BleGattCharacteristicProperty.WRITE) || it.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) }
    val notifiables = services.flatMap { it.characteristics }
        .filter { it.properties.contains(BleGattCharacteristicProperty.NOTIFY) || it.properties.contains(BleGattCharacteristicProperty.INDICATE) }

    Scaffold(topBar = {
        ChatTopBar(
            notifyStatusList = notifyStatus,
            notifiableList = notifiables,
            onToggleNotify = { ch, enable -> scope.launch { runCatching { connection.notify(ch, enable) } } }
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 订阅数量显示合并入订阅管理标题，减少顶部拥挤
            var dropdownExpanded by remember { mutableStateOf(false) }

            // 订阅管理已迁移至标题栏动作，页面不再占位

            // 通讯记录
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "通讯记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { messages.clear() }) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "清空")
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.size) { idx ->
                    val m = messages[idx]
                    MessageBubble(msg = m)
                }
            }
            // 发送特征选择（输入框上方）
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(
                        onClick = { dropdownExpanded = true },
                        label = { Text(selectedChar?.let { "特征: ${it.uuid}" } ?: "选择可写特征") },
                        leadingIcon = { Icon(imageVector = Icons.Outlined.Bluetooth, contentDescription = null) },
                        modifier = Modifier
                    )
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        writables.forEach { ch ->
                            DropdownMenuItem(
                                text = { Text(text = "${ch.serviceUuid} -> ${ch.uuid}") },
                                onClick = {
                                    selectedChar = ch
                                    dropdownExpanded = false
                                }
                            )
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
                                    messages.add(ChatMessage(Clock.System.now().toEpochMilliseconds(), "TX", "${ch.uuid}: ${text.trim()}"))
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

data class ChatMessage(val time: Long, val dir: String, val content: String)

@Composable
private fun MessageBubble(msg: ChatMessage) {
    
    val isTx = msg.dir == "TX"
    val container = if (isTx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (isTx) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val (meta, payload) = run {
        val parts = msg.content.split(": ", limit = 2)
        val meta = parts.getOrNull(0) ?: ""
        val body = parts.getOrNull(1) ?: msg.content
        meta to body
    }
    Surface(color = container, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = msg.dir, style = MaterialTheme.typography.labelMedium, color = onContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = meta, style = MaterialTheme.typography.labelSmall, color = onContainer)
            }
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.foundation.text.selection.SelectionContainer {
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