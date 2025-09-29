package com.d10ng.bluetooth.constant

/**
 * 蓝牙操作结果
 * @Author d10ng
 * @Date 2025/9/29 15:52
 */
sealed class OperationResult(
    open val address: String
) {
    // 连接结果
    data class Connect(override val address: String, val result: Boolean, val obj: Any?): OperationResult(address)
    // 服务发现结果
    data class DiscoverServices(override val address: String, val services: List<BluetoothGattService>): OperationResult(address)
    // 开关通知结果
    data class Notify(override val address: String, val serviceUuid: String, val characteristicUuid: String, val enable: Boolean, val result: Boolean): OperationResult(address)
    // 写数据结果
    data class Write(override val address: String, val serviceUuid: String, val characteristicUuid: String, val result: Boolean): OperationResult(address)
    // MTU改变结果
    data class MtuChanged(override val address: String, val mtu: Int, val result: Boolean): OperationResult(address)
}