package com.d10ng.bluetooth

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
expect object BluetoothControllerMultiplatform {

    fun startScan()
    fun stopScan()
    suspend fun connect(device: BluetoothDevice): List<BluetoothGattService>
    fun disconnect(device: BluetoothDevice)
    fun disconnectAll()
}