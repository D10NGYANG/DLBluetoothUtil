package com.d10ng.bluetooth.constant

/**
 * Manager/Connection 与平台 Runner 之间的内部操作协议。
 *
 * [address] 是串行调度键，[timeoutMillis] 覆盖等待地址锁、入队和原生回调的完整时间。
 * [Connect.obj] 等 `Any` 字段只承载平台句柄，不得提升到公共 Interface。
 */
internal sealed class OperationType(
    open val address: String,
    val timeoutMillis: Long = 5000
) {
    // 连接
    data class Connect(override val address: String, val obj: Any): OperationType(address, 5000) {
        fun fail() = OperationResult.Connect(address, false, null)
        fun success(obj: Any) = OperationResult.Connect(address, true, obj)
    }
    // 服务发现
    data class DiscoverServices(override val address: String, val obj: Any): OperationType(address, 3000) {
        fun fail() = OperationResult.DiscoverServices(address, listOf())
        fun success(services: List<BleGattService>) = OperationResult.DiscoverServices(address, services)
    }
    // 开关通知
    data class Notify(override val address: String, val characteristic: BleGattCharacteristic, val enable: Boolean, val obj: Any): OperationType(address, 1000) {
        fun fail() = OperationResult.Notify(address, characteristic, enable, false)
        fun success() = OperationResult.Notify(address, characteristic, enable, true)
    }
    // 写数据
    class Write(override val address: String, val characteristic: BleGattCharacteristic, val value: ByteArray, val obj: Any): OperationType(address, 1000) {
        fun fail() = OperationResult.Write(address, characteristic, false)
        fun success() = OperationResult.Write(address, characteristic, true)
    }
    // MTU改变
    data class MtuChanged(override val address: String, val mtu: Int, val obj: Any): OperationType(address, 1000) {
        fun fail() = OperationResult.MtuChanged(address, mtu, false)
        fun success(mtu: Int) = OperationResult.MtuChanged(address, mtu, true)
    }
}
