package com.d10ng.bluetooth.constant

/**
 * 蓝牙服务
 * @Author d10ng
 * @Date 2025/9/28 17:53
 */
data class BleGattService(
    val uuid: String,
    val characteristics: List<BleGattCharacteristic>
)
