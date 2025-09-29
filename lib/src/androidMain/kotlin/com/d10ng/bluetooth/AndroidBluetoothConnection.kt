package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BluetoothDevice
import com.d10ng.bluetooth.constant.BluetoothGattService

/**
 * Android蓝牙连接
 * @Author d10ng
 * @Date 2025/9/29 15:07
 */
class AndroidBluetoothConnection(
    device: BluetoothDevice
): ABluetoothConnection(device) {



    override suspend fun discoverServices(): List<BluetoothGattService> {
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