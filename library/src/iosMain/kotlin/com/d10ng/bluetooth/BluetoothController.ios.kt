package com.d10ng.bluetooth

/**
 * 获取蓝牙实现
 * @return IBluetoothController
 */
actual fun getBluetoothController(): IBluetoothController {
    return BluetoothControllerIOS
}