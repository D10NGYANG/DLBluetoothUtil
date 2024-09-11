package com.d10ng.bluetooth

/**
 * 蓝牙设备
 * @Author d10ng
 * @Date 2024/9/10 15:16
 */
data class BluetoothDevice(
    // 设备名称
    val name: String?,
    // 设备地址
    val address: String,
    // 设备信号
    val rssi: Int
)
