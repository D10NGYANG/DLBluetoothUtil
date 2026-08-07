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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBPeripheral
import kotlin.uuid.ExperimentalUuidApi

/**
 * 将 [OperationManager] 请求适配为 iOS CoreBluetooth 调用。
 *
 * 队列只有此处消费。每个请求在独立子协程中执行，使不同 peripheral 能够并发；同一地址的
 * 串行由 [OperationManager] 保证。所有等待函数都先订阅 delegate 事件，再发起 CoreBluetooth
 * 操作，以免快速回调发生在订阅建立之前。
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
        var delivered = false
        try {
            val event = awaitCentralEvent<CBCentralManagerEvent.DidConnectResult>(operation.address) {
                centralManager.connectPeripheral(device, null)
            }
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
        val event = awaitPeripheralEvent<CBPeripheralEvent.DidDiscoverServices>(operation.address) {
            device.discoverServices(null)
        }
        if (event.services.isNullOrEmpty()) {
            log.d { "[OperationType.DiscoverServices] fail 获取服务失败" }
            request.result.complete(operation.fail())
            return
        }
        val list = event.services.map { service ->
            val e = awaitPeripheralEvent<CBPeripheralEvent.DidDiscoverCharacteristicsForService>(
                operation.address,
                { it.service.UUIDString.contentEquals(service.UUIDString, true) }
            ) {
                device.discoverCharacteristics(null, service)
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

    private suspend fun notify(request: OperationRequest, operation: OperationType.Notify) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.nativeHandle as CBCharacteristic
        val supportsNotify = operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)
        val supportsIndicate = operation.characteristic.properties.contains(BleGattCharacteristicProperty.INDICATE)
        if (!supportsNotify && !supportsIndicate) {
            log.w { "[OperationType.Notify] fail 特征不支持通知" }
            request.result.complete(operation.fail())
            return
        }
        if (characteristic.isNotifying == operation.enable) {
            request.result.complete(operation.success())
            return
        }
        val event = awaitPeripheralEvent<CBPeripheralEvent.DidUpdateNotificationState>(
            operation.address,
            { it.characteristic.matches(characteristic) }
        ) {
            device.setNotifyValue(operation.enable, characteristic)
        }
        if (!event.result || characteristic.isNotifying != operation.enable) {
            log.w { "[OperationType.Notify] fail 设置通知失败" }
            request.result.complete(operation.fail())
            return
        }
        request.result.complete(operation.success())
    }

    private suspend fun write(request: OperationRequest, operation: OperationType.Write) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.nativeHandle as CBCharacteristic
        val writeType = when {
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE) -> CBCharacteristicWriteWithResponse
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) -> CBCharacteristicWriteWithoutResponse
            else -> {
                log.w { "[OperationType.Write] fail 特征不支持写入" }
                request.result.complete(operation.fail())
                return
            }
        }
        if (writeType == CBCharacteristicWriteWithResponse) {
            val event = awaitPeripheralEvent<CBPeripheralEvent.DidWriteValueForCharacteristic>(
                device.address,
                { it.characteristic.matches(characteristic) }
            ) {
                device.writeValue(operation.value.toNSData(), characteristic, writeType)
            }
            if (!event.result) {
                log.w { "[OperationTypeWrite] fail 写入失败" }
                request.result.complete(operation.fail())
                return
            }
            request.result.complete(operation.success())
        } else {
            awaitCanSendWriteWithoutResponse(device)
            device.writeValue(operation.value.toNSData(), characteristic, writeType)
            request.result.complete(operation.success())
        }
    }

    private fun requestMtu(request: OperationRequest, operation: OperationType.MtuChanged) {
        val device = operation.obj as CBPeripheral
        val mtu = device.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse)
        request.result.complete(operation.success(mtu.toInt()))
    }

    private suspend inline fun <reified T : CBCentralManagerEvent> awaitCentralEvent(
        address: String,
        crossinline start: () -> Unit
    ): T = coroutineScope {
        // UNDISPATCHED 是这里的时序约束：订阅必须先于 start()。
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            BleCentralEvents.first<T>(address)
        }
        start()
        event.await()
    }

    private suspend inline fun <reified T : CBPeripheralEvent> awaitPeripheralEvent(
        address: String,
        crossinline predicate: (T) -> Boolean = { true },
        crossinline start: () -> Unit
    ): T = coroutineScope {
        // 与中心管理器事件相同，先订阅再发起 peripheral 操作。
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            BlePeripheralEvents.first<T>(address, predicate)
        }
        start()
        event.await()
    }

    private suspend fun awaitCanSendWriteWithoutResponse(device: CBPeripheral) {
        if (device.canSendWriteWithoutResponse) return
        coroutineScope {
            val ready = async(start = CoroutineStart.UNDISPATCHED) {
                BlePeripheralEvents.first<CBPeripheralEvent.IsReadyToSendWriteWithoutResponse>(device.address)
            }
            if (device.canSendWriteWithoutResponse) ready.cancel() else ready.await()
        }
    }

    private fun CBCharacteristic.matches(other: CBCharacteristic): Boolean =
        UUIDString.contentEquals(other.UUIDString, true) &&
                serviceUUIDString.contentEquals(other.serviceUUIDString, true)
}
