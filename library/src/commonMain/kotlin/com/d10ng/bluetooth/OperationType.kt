package com.d10ng.bluetooth

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/**
 * 蓝牙操作类型
 * @Author d10ng
 * @Date 2025/8/13 14:59
 */
interface OperationType {}

// 连接
data class OperationTypeConnect(val address: String): OperationType {
    fun fail() = OperationResultConnect(address, false)
    fun success() = OperationResultConnect(address, true)
}
// 服务发现
data class OperationTypeDiscoverServices(val address: String): OperationType {
    fun fail() = OperationResultDiscoverServices(address, listOf())
    fun success(services: List<BluetoothGattService>) = OperationResultDiscoverServices(address, services)
}
// 开关通知
data class OperationTypeNotify(val address: String, val serviceUuid: String, val characteristicUuid: String, val enable: Boolean): OperationType {
    fun fail() = OperationResultNotify(address, serviceUuid, characteristicUuid, enable, false)
    fun success() = OperationResultNotify(address, serviceUuid, characteristicUuid, enable, true)
}
// 写数据
class OperationTypeWrite(val address: String, val serviceUuid: String, val characteristicUuid: String, val value: ByteArray): OperationType {
    fun fail() = OperationResultWrite(address, serviceUuid, characteristicUuid, false)
    fun success() = OperationResultWrite(address, serviceUuid, characteristicUuid, true)
}
// MTU改变
data class OperationTypeMtuChanged(val address: String, val mtu: Int): OperationType {
    fun fail() = OperationResultMtuChanged(address, mtu, false)
    fun success(mtu: Int) = OperationResultMtuChanged(address, mtu, true)
}

// 操作结果
interface OperationResult {
    val address: String
}

// 连接结果
data class OperationResultConnect(override val address: String, val result: Boolean): OperationResult
// 服务发现结果
data class OperationResultDiscoverServices(override val address: String, val services: List<BluetoothGattService>): OperationResult
// 开关通知结果
data class OperationResultNotify(override val address: String, val serviceUuid: String, val characteristicUuid: String, val enable: Boolean, val result: Boolean): OperationResult
// 写数据结果
data class OperationResultWrite(override val address: String, val serviceUuid: String, val characteristicUuid: String, val result: Boolean): OperationResult
// MTU改变结果
data class OperationResultMtuChanged(override val address: String, val mtu: Int, val result: Boolean): OperationResult

suspend inline fun <reified T: OperationResult> SharedFlow<OperationResult>.awaitFirstOperationResult(
    address: String,
    crossinline predicate: (T) -> Boolean = { true }
): T {
    return this.first {
        it is T && it.address.contentEquals(address, true) && predicate(it)
    } as T
}