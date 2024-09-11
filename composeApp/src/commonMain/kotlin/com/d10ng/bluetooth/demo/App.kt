package com.d10ng.bluetooth.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d10ng.bluetooth.BluetoothController
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val scanDevice by BluetoothController.scanDevicesFlow.collectAsState()
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
                        if (BluetoothController.scanningFlow.value)
                            BluetoothController.cancelScan()
                        else BluetoothController.startScan()
                    }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(scanDevice) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(Color.LightGray)
                            .padding(8.dp)
                    ) {
                        Text("${item.name} - ${item.address} - ${item.rssi}")
                    }
                }
            }
        }
    }
}