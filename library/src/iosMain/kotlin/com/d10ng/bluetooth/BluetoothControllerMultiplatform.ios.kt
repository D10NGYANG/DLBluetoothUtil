package com.d10ng.bluetooth

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
actual object BluetoothControllerMultiplatform {
    actual fun startScan() {
    }

    actual fun stopScan() {
    }

    actual suspend fun connect(device: BluetoothDevice) {
    }

    actual fun disconnect(device: BluetoothDevice) {
    }

    actual fun disconnectAll() {
    }

}