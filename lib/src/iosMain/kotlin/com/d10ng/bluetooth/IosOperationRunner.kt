package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBPeripheralEvent
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
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

    private var _centralManager: CBCentralManager? = null

    internal val centralManager: CBCentralManager
        get() {
            if (_centralManager == null) {
                _centralManager = createCentralManager(false)
            }
            return _centralManager!!
        }

    private fun createCentralManager(showPowerAlert: Boolean): CBCentralManager {
        return CBCentralManager(
            delegate = CBCentralManagerDelegate,
            queue = null,
            options = mapOf<Any?, Any>(CBCentralManagerOptionShowPowerAlertKey to showPowerAlert)
        )
    }

    fun restartCentralManager() {
        _centralManager = createCentralManager(true)
    }

    fun start() {
        log.d { "IosOperationRunner start" }
        // 确保初始化
        centralManager
    }

    init {
        scope.launch {
            for (request in OperationManager.queueChannel) {
                val operation = request.operation
                val job = launch(start = CoroutineStart.LAZY) {
                    when (operation) {
                        is OperationType.Connect -> {
                            // 连接
                            connect(request, operation)
                        }
                        is OperationType.DiscoverServices -> {
                            // 服务发现
                            discoverServices(request, operation)
                        }
                        is OperationType.Notify -> {
                            // 开关通知
                            notify(request, operation)
                        }
                        is OperationType.Write -> {
                            // 写入
                            write(request, operation)
                        }
                        is OperationType.MtuChanged -> {
                            // 修改MTU
                            requestMtu(request, operation)
                        }
                    }
                }
                request.result.invokeOnCompletion {
                    if (request.result.isCancelled) job.cancel()
                }
                job.start()
            }
        }
    }

    private suspend fun connect(request: OperationRequest, operation: OperationType.Connect) {
        val device = operation.obj as CBPeripheral
        centralManager.connectPeripheral(device, null)
        var delivered = false
        try {
            val event = BleCentralEvents.first<CBCentralManagerEvent.DidConnectResult>(operation.address)
            if (event.result) {
                log.d { "[OperationType.Connect] success 连接成功" }
                delivered = request.result.complete(operation.success(event.peripheral))
            } else {
                log.d { "[OperationType.Connect] fail 连接失败" }
                request.result.complete(operation.fail())
            }
        } finally {
            if (!delivered) centralManager.cancelPeripheralConnection(device)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun discoverServices(request: OperationRequest, operation: OperationType.DiscoverServices) {
        val device = operation.obj as CBPeripheral
        device.delegate = CBPeripheralDelegate
        device.discoverServices(null)
        val event = BlePeripheralEvents.first<CBPeripheralEvent.DidDiscoverServices>(operation.address)
        if (event.services.isNullOrEmpty()) {
            log.d { "[OperationType.DiscoverServices] fail 获取服务失败" }
            request.result.complete(operation.fail())
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
        request.result.complete(operation.success(map))
    }

    private fun notify(request: OperationRequest, operation: OperationType.Notify) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.obj as CBCharacteristic
        if (!operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)) {
            log.w { "[OperationType.Notify] fail 特征不支持通知" }
            request.result.complete(operation.fail())
            return
        }
        if (characteristic.isNotifying != operation.enable) {
            device.setNotifyValue(operation.enable, characteristic)
        }
        request.result.complete(operation.success())
    }

    private suspend fun write(request: OperationRequest, operation: OperationType.Write) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.obj as CBCharacteristic
        val writeType = when {
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE) -> CBCharacteristicWriteWithResponse
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) -> CBCharacteristicWriteWithoutResponse
            else -> {
                log.w { "[OperationType.Write] fail 特征不支持写入" }
                request.result.complete(operation.fail())
                return
            }
        }
        device.writeValue(operation.value.toNSData(), characteristic, writeType)
        if (writeType == CBCharacteristicWriteWithResponse) {
            val event = BlePeripheralEvents.first<CBPeripheralEvent.DidWriteValueForCharacteristic>(device.address)
            if (!event.result) {
                log.w { "[OperationTypeWrite] fail 写入失败" }
                request.result.complete(operation.fail())
                return
            }
            request.result.complete(operation.success())
        } else {
            BlePeripheralEvents.first<CBPeripheralEvent.IsReadyToSendWriteWithoutResponse>(device.address)
            request.result.complete(operation.success())
        }
    }

    private fun requestMtu(request: OperationRequest, operation: OperationType.MtuChanged) {
        val device = operation.obj as CBPeripheral
        val mtu = device.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse)
        request.result.complete(operation.success(mtu.toInt()))
    }
}
