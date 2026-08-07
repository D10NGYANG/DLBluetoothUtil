package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBPeripheralEvent
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import kotlin.uuid.ExperimentalUuidApi

/**
 * iOS 连接 Adapter。
 *
 * 初始化时先订阅中心管理器断开事件和 peripheral 通知，[awaitReady] 完成后管理器才把实例交给
 * 调用方。连接结束时清空公共状态并取消内部协程，避免旧连接继续接收全局聚合器事件。
 */
internal class IosBleConnection(
    device: BleDevice
) : ABleConnection(device) {

    internal val peripheral = device.nativeHandle as CBPeripheral

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            // 监听中心管理器断开事件，更新连接状态
            launch(start = CoroutineStart.UNDISPATCHED) {
                BleCentralEvents.eventFlow
                    .filter { event -> event is CBCentralManagerEvent.DidDisconnect }
                    .filter { event -> event.peripheral.address.contentEquals(peripheral.address, true) }
                    .collect { handleDisconnected() }
            }

            // 监听特征值通知，转发为通用通知数据
            launch(start = CoroutineStart.UNDISPATCHED) {
                BlePeripheralEvents.notificationFlow
                    .filter { event -> event.peripheral.address.contentEquals(peripheral.address, true) }
                    .collect { event ->
                        val ch = event.characteristic
                        val data = event.data
                        val characteristic = runCatching { ch.toBleGattCharacteristic() }
                            .getOrNull() ?: return@collect
                        mutableNotifyDataFlow.tryEmit(BleGattNotifyData(characteristic, data))
                    }
            }

            ready.complete(Unit)
        }
    }

    /** 等待内部事件订阅建立；仅由 [IosBleManager] 在返回连接前调用。 */
    suspend fun awaitReady() = ready.await()

    @OptIn(ExperimentalUuidApi::class)
    private fun CBCharacteristic.toBleGattCharacteristic(): BleGattCharacteristic {
        return mutableServicesFlow.value
            .first { service -> service.uuid.contentEquals(this.service?.UUIDString, true) }
            .characteristics
            .first { char -> char.uuid.contentEquals(this.UUIDString, true) }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        val result = OperationManager.execute<OperationResult.DiscoverServices>(OperationType.DiscoverServices(device.address, peripheral))
        if (result == null || result.services.isEmpty()) throw Exception("discover services failed")
        mutableServicesFlow.value = result.services
        return result.services
    }

    override suspend fun requestMaxMtu(): Int {
        val result = OperationManager.execute<OperationResult.MtuChanged>(OperationType.MtuChanged(device.address, GATT_MAX_MTU_SIZE, peripheral))
        if (result == null || !result.result) {
            log.w { "request mtu failed" }
        } else {
            log.i { "set mtu to ${result.mtu} success" }
        }
        return if (result?.result == true) result.mtu else GATT_MIN_MTU_SIZE - 3
    }

    override suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray) {
        val result = OperationManager.execute<OperationResult.Write>(OperationType.Write(device.address, characteristic, value, peripheral))
        if (result == null || !result.result) throw Exception("write failed")
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean) {
        val result = OperationManager.execute<OperationResult.Notify>(OperationType.Notify(device.address, characteristic, enable, peripheral))
        if (result == null || !result.result) throw Exception("notify failed")
        updateNotifyStatus(characteristic, enable)
    }

    override fun disconnect() {
        if (!mutableIsConnectedFlow.value) return
        runCatching { IosOperationRunner.centralManager.cancelPeripheralConnection(peripheral) }
        handleDisconnected()
    }

    private fun handleDisconnected() {
        if (!mutableIsConnectedFlow.value) return
        mutableIsConnectedFlow.value = false
        mutableServicesFlow.value = emptyList()
        mutableNotifyStatusFlow.value = emptyList()
        scope.cancel()
    }
}
