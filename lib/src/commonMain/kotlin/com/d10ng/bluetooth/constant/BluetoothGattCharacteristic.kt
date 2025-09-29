package com.d10ng.bluetooth.constant

/**
 * 蓝牙特征值
 * @Author d10ng
 * @Date 2025/9/28 17:52
 */
data class BluetoothGattCharacteristic(
    val uuid: String,
    val serviceUuid: String,
    val properties: Set<BluetoothGattCharacteristicProperty>
)
