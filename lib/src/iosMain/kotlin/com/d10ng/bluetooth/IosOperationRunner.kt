package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBPeripheralEvent
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBPeripheral
import kotlin.uuid.ExperimentalUuidApi

/**
 * ios操作执行器
 * @Author d10ng
 * @Date 2025/9/30 14:19
 */
object IosOperationRunner {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal val centralManager by lazy { CBCentralManager(delegate = CBCentralManagerDelegate, queue = null) }

    fun start() {
        log.d { "IosOperationRunner start" }
    }

    init {
        scope.launch {
            for (operation in OperationManager.queueChannel) {
                when (operation) {
                    is OperationType.Connect -> {
                        // 连接
                        launch { connect(operation) }
                    }
                    is OperationType.DiscoverServices -> {
                        // 服务发现
                        launch { discoverServices(operation) }
                    }
                    is OperationType.Notify -> {
                        // 开关通知
                        launch { notify(operation) }
                    }
                    is OperationType.Write -> {
                        // 写入
                        launch { write(operation) }
                    }
                    is OperationType.MtuChanged -> {
                        // 修改MTU
                        launch { requestMtu(operation) }
                    }
                }
            }
        }
    }

    private suspend fun connect(operation: OperationType.Connect) {
        val device = operation.obj as CBPeripheral
        centralManager.connectPeripheral(device, null)
        val event = BleCentralEvents.first<CBCentralManagerEvent.DidConnectResult>(operation.address)
        if (event.result) {
            log.d { "[OperationType.Connect] success 连接成功" }
            OperationManager.resultFlow.tryEmit(operation.success(event.peripheral))
        } else {
            log.d { "[OperationType.Connect] fail 连接失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun discoverServices(operation: OperationType.DiscoverServices) {
        val device = operation.obj as CBPeripheral
        device.delegate = CBPeripheralDelegate
        device.discoverServices(null)
        val event = BlePeripheralEvents.first<CBPeripheralEvent.DidDiscoverServices>(operation.address)
        if (event.services.isNullOrEmpty()) {
            log.d { "[OperationType.DiscoverServices] fail 获取服务失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        val list = event.services.map { service ->
            device.discoverCharacteristics(null, service)
            val e = BlePeripheralEvents.first<CBPeripheralEvent.DidDiscoverCharacteristicsForService>(operation.address) {
                it.peripheral.address.contentEquals(device.address, true)
                        && it.service.UUIDString.contentEquals(service.UUIDString, true)
            }
            service to (e.characteristics ?: listOf())
        }
        val map = list.map { (service, characteristics) ->
            BleGattService(
                service.UUIDString,
                characteristics.map { ch ->
                    BleGattCharacteristic(
                        ch.UUIDString,
                        service.UUIDString,
                        BleGattCharacteristicProperty.fromValue(ch.properties.toInt()),
                        ch
                    )
                },
                service
            )
        }
        OperationManager.resultFlow.tryEmit(operation.success(map))
    }

    private fun notify(operation: OperationType.Notify) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.obj as CBCharacteristic
        if (!operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)) {
            log.w { "[OperationType.Notify] fail 特征不支持通知" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        if (characteristic.isNotifying != operation.enable) {
            device.setNotifyValue(operation.enable, characteristic)
        }
        OperationManager.resultFlow.tryEmit(operation.success())
    }

    private suspend fun write(operation: OperationType.Write) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.obj as CBCharacteristic
        val writeType = when {
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE) -> CBCharacteristicWriteWithResponse
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) -> CBCharacteristicWriteWithoutResponse
            else -> {
                log.w { "[OperationType.Write] fail 特征不支持写入" }
                OperationManager.resultFlow.tryEmit(operation.fail())
                return
            }
        }
        device.writeValue(operation.value.toNSData(), characteristic, writeType)
        if (writeType == CBCharacteristicWriteWithResponse) {
            val event = BlePeripheralEvents.first<CBPeripheralEvent.DidWriteValueForCharacteristic>(device.address)
            if (!event.result) {
                log.w { "[OperationTypeWrite] fail 写入失败" }
                OperationManager.resultFlow.tryEmit(operation.fail())
                return
            }
            OperationManager.resultFlow.tryEmit(operation.success())
        } else {
            BlePeripheralEvents.first<CBPeripheralEvent.IsReadyToSendWriteWithoutResponse>(device.address)
            OperationManager.resultFlow.tryEmit(operation.success())
        }
    }

    private fun requestMtu(operation: OperationType.MtuChanged) {
        val device = operation.obj as CBPeripheral
        val mtu = device.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse)
        OperationManager.resultFlow.tryEmit(operation.success(mtu.toInt()))
    }
}