package com.d10ng.bluetooth.constant

/**
 * 平台 Runner 返回给 Manager/Connection 的内部结果协议。
 *
 * 结果只描述平台操作是否完成及必要数据；公共层负责把失败或超时转换成异常、回退值和状态 Flow。
 */
internal sealed class OperationResult(
    open val address: String
) {
    // 连接结果
    data class Connect(override val address: String, val result: Boolean, val obj: Any?): OperationResult(address)
    // 服务发现结果
    data class DiscoverServices(override val address: String, val services: List<BleGattService>): OperationResult(address)
    // 开关通知结果
    data class Notify(override val address: String, val characteristic: BleGattCharacteristic, val enable: Boolean, val result: Boolean): OperationResult(address)
    // 写数据结果
    data class Write(override val address: String, val characteristic: BleGattCharacteristic, val result: Boolean): OperationResult(address)
    // MTU改变结果
    data class MtuChanged(override val address: String, val mtu: Int, val result: Boolean): OperationResult(address)
}
