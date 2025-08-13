package com.d10ng.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/**
 * 蓝牙BLE GATT操作响应
 * @Author d10ng
 * @Date 2025/8/13 15:14
 */

internal interface BluetoothGattEvent {
    val gatt: BluetoothGatt
}
internal data class BluetoothGattOnConnectionStateChangeEvent(override val gatt: BluetoothGatt, val status: Int, val newState: Int): BluetoothGattEvent
internal data class BluetoothGattOnServicesDiscoveredEvent(override val gatt: BluetoothGatt, val status: Int): BluetoothGattEvent
internal data class BluetoothGattOnCharacteristicWriteEvent(override val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic, val status: Int): BluetoothGattEvent
internal data class BluetoothGattOnDescriptorWriteEvent(override val gatt: BluetoothGatt, val descriptor: BluetoothGattDescriptor, val status: Int): BluetoothGattEvent
internal class BluetoothGattOnCharacteristicChangedEvent(override val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic, val value: ByteArray): BluetoothGattEvent
internal data class BluetoothGattOnMtuChangedEvent(override val gatt: BluetoothGatt, val mtu: Int, val status: Int): BluetoothGattEvent

