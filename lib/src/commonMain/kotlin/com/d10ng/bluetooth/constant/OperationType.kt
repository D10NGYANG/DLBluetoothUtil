package com.d10ng.bluetooth.constant

/**
 * 蓝牙操作类型
 * @Author d10ng
 * @Date 2025/9/29 15:49
 */
sealed class OperationType(
    open val address: String,
    val timeoutMillis: Long = 5000
) {
    // 连接
    data class Connect(override val address: String): OperationType(address, 5000) {
        fun fail() = OperationResult.Connect(address, false)
        fun success() = OperationResult.Connect(address, true)
    }
    // 服务发现
    data class DiscoverServices(override val address: String): OperationType(address, 3000) {
        fun fail() = OperationResult.DiscoverServices(address, listOf())
        fun success(services: List<BluetoothGattService>) = OperationResult.DiscoverServices(address, services)
    }
    // 开关通知
    data class Notify(override val address: String, val serviceUuid: String, val characteristicUuid: String, val enable: Boolean): OperationType(address, 1000) {
        fun fail() = OperationResult.Notify(address, serviceUuid, characteristicUuid, enable, false)
        fun success() = OperationResult.Notify(address, serviceUuid, characteristicUuid, enable, true)
    }
    // 写数据
    class Write(override val address: String, val serviceUuid: String, val characteristicUuid: String, val value: ByteArray): OperationType(address, 1000) {
        fun fail() = OperationResult.Write(address, serviceUuid, characteristicUuid, false)
        fun success() = OperationResult.Write(address, serviceUuid, characteristicUuid, true)
    }
    // MTU改变
    data class MtuChanged(override val address: String, val mtu: Int): OperationType(address, 1000) {
        fun fail() = OperationResult.MtuChanged(address, mtu, false)
        fun success(mtu: Int) = OperationResult.MtuChanged(address, mtu, true)
    }
}