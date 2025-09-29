package com.d10ng.bluetooth.constant

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/**
 * 蓝牙事件
 * @Author d10ng
 * @Date 2025/9/29 16:25
 */
sealed class BleGattEvent(
    open val gatt: BluetoothGatt
) {
    data class OnConnectionStateChange(override val gatt: BluetoothGatt, val status: Int, val newState: Int): BleGattEvent(gatt)
    data class OnServicesDiscovered(override val gatt: BluetoothGatt, val status: Int): BleGattEvent(gatt)
    data class OnCharacteristicWrite(override val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic, val status: Int): BleGattEvent(gatt)
    data class OnDescriptorWrite(override val gatt: BluetoothGatt, val descriptor: BluetoothGattDescriptor, val status: Int): BleGattEvent(gatt)
    class OnCharacteristicChanged(override val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic, val value: ByteArray): BleGattEvent(gatt)
    data class OnMtuChanged(override val gatt: BluetoothGatt, val mtu: Int, val status: Int): BleGattEvent(gatt)
}