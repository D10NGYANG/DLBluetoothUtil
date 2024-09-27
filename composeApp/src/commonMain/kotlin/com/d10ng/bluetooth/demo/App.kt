package com.d10ng.bluetooth.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d10ng.bluetooth.BluetoothController
import com.d10ng.bluetooth.BluetoothGattCharacteristic
import com.d10ng.bluetooth.BluetoothGattService
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            val scanDevices by BluetoothController.scanDevicesFlow.collectAsState()
            val connectedDevices by BluetoothController.connectedDevicesFlow.collectAsState()
            val scope = rememberCoroutineScope()
            val hasConnected = remember(connectedDevices) { connectedDevices.isNotEmpty() }
            val services = remember { mutableStateListOf<BluetoothGattService>() }
            val notifyCharacteristics = remember { mutableStateListOf<BluetoothGattCharacteristic>() }
            var notifyContent by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                launch {
                    BluetoothController.setDebug(true)
                    BluetoothController.notifyDataFlow.collect {
                        val (_, data) = it
                        notifyContent = data.decodeToString()
                    }
                }
            }
            LaunchedEffect(connectedDevices) {
                if (connectedDevices.isEmpty()) {
                    services.clear()
                    notifyCharacteristics.clear()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                Text(
                    text = "蓝牙设备",
                    style = TextStyle(fontSize = 22.sp, color = Color.Black, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .padding(16.dp)
                        .clickable {
                            println("isBleEnable = ${BluetoothController.isBleEnable()}")
                            if (BluetoothController.scanningFlow.value)
                                BluetoothController.cancelScan()
                            else BluetoothController.startScan()
                        }
                )
                if (hasConnected) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        item {
                            Text(
                                text = "已连接设备: ${connectedDevices[0].name}",
                                style = TextStyle(fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clickable { BluetoothController.disconnect(connectedDevices[0]) }
                            )
                        }
                        items(services) { service ->
                            Text(
                                text = "服务：${service.uuid}",
                                style = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .background(Color.Black)
                                    .padding(16.dp)
                            )
                            service.characteristics.forEach { characteristic ->
                                Text(
                                    text = "特征值：${characteristic.uuid}",
                                    style = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Normal),
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 2.dp)
                                        .background(Color.DarkGray)
                                        .padding(16.dp)
                                )
                                Text(
                                    text = characteristic.getProperties().joinToString { it.name },
                                    style = TextStyle(fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Normal),
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 2.dp)
                                        .background(if (notifyCharacteristics.contains(characteristic)) Color.Cyan else Color.Yellow)
                                        .padding(16.dp)
                                        .clickable {
                                            if (characteristic.isNotifiable()) {
                                                val enable = notifyCharacteristics.contains(characteristic)
                                                BluetoothController.notify(connectedDevices[0].address, service.uuid, characteristic.uuid, enable.not())
                                                if (enable) notifyCharacteristics.remove(characteristic) else notifyCharacteristics.add(characteristic)
                                            } else if (characteristic.isWriteable()) {
                                                scope.launch {
                                                    runCatching {
                                                        val data = "\$CCSZX,1,16001004*62\r\n".encodeToByteArray()
                                                        BluetoothController.write(connectedDevices[0].address, service.uuid, characteristic.uuid, data)
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(100.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        items(scanDevices) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(Color.LightGray)
                                    .padding(8.dp)
                                    .clickable {
                                        scope.launch {
                                            services.clear()
                                            services.addAll(BluetoothController.connect(item))
                                        }
                                    }
                            ) {
                                Text("${item.name} - ${item.address} - ${item.rssi}")
                            }
                        }
                    }
                }
            }
            if (notifyContent.isNotEmpty()) {
                Text(
                    text = notifyContent,
                    style = TextStyle(fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Normal),
                    modifier = Modifier
                        .padding(16.dp)
                        .background(Color.Cyan, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}