package com.d10ng.bluetooth

import android.bluetooth.BluetoothGatt
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattService

/**
 * Android蓝牙连接
 * @Author d10ng
 * @Date 2025/9/29 15:07
 */
class AndroidBleConnection(
    device: BleDevice,
    val gatt: BluetoothGatt
): ABleConnection(device) {



    override suspend fun discoverServices(): List<BleGattService> {
        TODO("Not yet implemented")
    }

    override suspend fun requestMaxMtu(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun write(
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun notify(
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun disconnect() {
        TODO("Not yet implemented")
    }
}