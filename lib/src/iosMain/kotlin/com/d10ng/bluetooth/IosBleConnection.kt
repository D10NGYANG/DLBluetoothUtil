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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import kotlin.uuid.ExperimentalUuidApi

/**
 * iOS蓝牙连接
 * @Author d10ng
 * @Date 2025/9/30 17:20
 */
class IosBleConnection(
    device: BleDevice
) : ABleConnection(device) {

    private val peripheral = device.obj as CBPeripheral

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ready = CompletableDeferred<Unit>()

    // 用于主动断开连接
    private val centralManager by lazy { IosOperationRunner.centralManager }

    init {
        scope.launch {
            // 监听中心管理器断开事件，更新连接状态
            launch {
                BleCentralEvents.eventFlow
                    .filter { event -> event is CBCentralManagerEvent.DidDisconnect }
                    .filter { event -> event.peripheral.address.contentEquals(peripheral.address, true) }
                    .collect { handleDisconnected() }
            }

            // 监听特征值通知，转发为通用通知数据
            launch {
                BlePeripheralEvents.eventFlow
                    .filter { event -> event is CBPeripheralEvent.DidUpdateValueForCharacteristic }
                    .filter { event -> event.peripheral.address.contentEquals(peripheral.address, true) }
                    .collect { event ->
                        event as CBPeripheralEvent.DidUpdateValueForCharacteristic
                        val ch = event.characteristic
                        val data = event.data
                        notifyDataFlow.tryEmit(BleGattNotifyData(ch.toBleGattCharacteristic(), data))
                    }
            }

            ready.complete(Unit)
        }
    }

    /**
     * 等待初始化完成
     */
    suspend fun awaitReady() = ready.await()

    @OptIn(ExperimentalUuidApi::class)
    private fun CBCharacteristic.toBleGattCharacteristic(): BleGattCharacteristic {
        return servicesFlow.value
            .first { service -> service.uuid == this.service!!.Uuid }
            .characteristics
            .first { char -> char.uuid == this.Uuid }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        val result = OperationManager.execute<OperationResult.DiscoverServices>(OperationType.DiscoverServices(device.address, peripheral))
        if (result == null || result.services.isEmpty()) throw Exception("discover services failed")
        servicesFlow.value = result.services
        return result.services
    }

    override suspend fun requestMaxMtu(): Int {
        val result = OperationManager.execute<OperationResult.MtuChanged>(OperationType.MtuChanged(device.address, GATT_MAX_MTU_SIZE, peripheral))
        if (result == null || !result.result) {
            log.w { "request mtu failed" }
        } else {
            log.i { "set mtu to ${result.mtu} success" }
        }
        return (if (result?.result == true) result.mtu else GATT_MIN_MTU_SIZE) - 3
    }

    override suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray) {
        val result = OperationManager.execute<OperationResult.Write>(OperationType.Write(device.address, characteristic, value, peripheral))
        if (result == null || !result.result) throw Exception("write failed")
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean) {
        val result = OperationManager.execute<OperationResult.Notify>(OperationType.Notify(device.address, characteristic, enable, peripheral))
        if (result == null || !result.result) throw Exception("notify failed")
        val ls = notifyStatusFlow.value.filter { it.uuid != characteristic.uuid }.toMutableList()
        if (enable) ls += characteristic
        notifyStatusFlow.value = ls
    }

    override suspend fun disconnect() {
        runCatching { centralManager.cancelPeripheralConnection(peripheral) }
        handleDisconnected()
    }

    private fun handleDisconnected() {
        isConnectedFlow.value = false
        servicesFlow.value = listOf()
        notifyStatusFlow.value = listOf()
    }
}