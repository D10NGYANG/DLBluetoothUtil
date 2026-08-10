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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralStateDisconnected
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
    private val disconnectEvents = MutableSharedFlow<CBPeripheral>(extraBufferCapacity = 16)

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
        log.i { "[central_manager.restart] showPowerAlert=true" }
        _centralManager = createCentralManager(true)
    }

    fun start() {
        log.d { "IosOperationRunner start" }
        // 确保初始化
        centralManager
    }

    internal fun onPeripheralDisconnected(peripheral: CBPeripheral) {
        log.d { "[disconnect.event_forwarded] address=${peripheral.address} name=${peripheral.name()}" }
        disconnectEvents.tryEmit(peripheral)
    }

    init {
        scope.launch {
            for (request in OperationManager.queueChannel) {
                val operation = request.operation
                log.d { "[operation.runner_received] ${operation.logFields(request.id)}" }
                val job = launch(start = CoroutineStart.LAZY) {
                    when (operation) {
                        is OperationType.Connect -> connect(request, operation)
                        is OperationType.DiscoverServices -> discoverServices(request, operation)
                        is OperationType.Notify -> notify(request, operation)
                        is OperationType.Write -> write(request, operation)
                        is OperationType.MtuChanged -> requestMtu(request, operation)
                    }
                }
                request.result.invokeOnCompletion {
                    if (request.result.isCancelled) {
                        scope.launch {
                            job.cancelAndJoin()
                            cleanupCancelledOperation(request)
                        }
                    }
                }
                job.start()
            }
        }
    }

    private suspend fun connect(request: OperationRequest, operation: OperationType.Connect) {
        val device = operation.obj as CBPeripheral
        log.i {
            "[connect.start] ${operation.logFields(request.id)} name=${device.name()}"
        }
        var delivered = false
        try {
            val event = awaitCentralEvent<CBCentralManagerEvent.DidConnectResult>(operation.address) {
                centralManager.connectPeripheral(device, null)
            }
            if (event.result) {
                log.i {
                    "[connect.success] ${operation.logFields(request.id)} name=${device.name()}"
                }
                delivered = request.result.complete(operation.success(event.peripheral))
            } else {
                log.w {
                    "[connect.failed] ${operation.logFields(request.id)} name=${device.name()}"
                }
                request.result.complete(operation.fail())
            }
        } finally {
            if (!delivered) {
                log.w {
                    "[connect.cleanup] ${operation.logFields(request.id)} reason=connection_not_delivered"
                }
                runCatching { centralManager.cancelPeripheralConnection(device) }.onFailure { error ->
                    log.e {
                        "[connect.cleanup_failed] ${operation.logFields(request.id)} " +
                                "error=${error.stackTraceToString()}"
                    }
                }
            }
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
            log.w {
                "[discover_services.failed] ${operation.logFields(request.id)} reason=no_services"
            }
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
        log.i {
            "[discover_services.success] ${operation.logFields(request.id)} " +
                    "services=${map.size} characteristics=${map.sumOf { it.characteristics.size }} " +
                    "serviceDetails=${map.toServiceDiscoveryLog()}"
        }
        request.result.complete(operation.success(map))
    }

    private suspend fun notify(request: OperationRequest, operation: OperationType.Notify) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.nativeHandle as CBCharacteristic
        val supportsNotify = operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)
        val supportsIndicate = operation.characteristic.properties.contains(BleGattCharacteristicProperty.INDICATE)
        if (!supportsNotify && !supportsIndicate) {
            log.w { "[notify.failed] ${operation.logFields(request.id)} reason=unsupported" }
            request.result.complete(operation.fail())
            return
        }
        if (characteristic.isNotifying == operation.enable) {
            log.d { "[notify.success] ${operation.logFields(request.id)} reason=already_in_requested_state" }
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
            log.w {
                "[notify.failed] ${operation.logFields(request.id)} " +
                        "reason=native_state_mismatch nativeIsNotifying=${characteristic.isNotifying} callbackResult=${event.result}"
            }
            request.result.complete(operation.fail())
            return
        }
        log.i { "[notify.success] ${operation.logFields(request.id)}" }
        request.result.complete(operation.success())
    }

    private suspend fun write(request: OperationRequest, operation: OperationType.Write) {
        val device = operation.obj as CBPeripheral
        val characteristic = operation.characteristic.nativeHandle as CBCharacteristic
        val writeType = when {
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE) -> CBCharacteristicWriteWithResponse
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) -> CBCharacteristicWriteWithoutResponse
            else -> {
                log.w { "[write.failed] ${operation.logFields(request.id)} reason=unsupported" }
                request.result.complete(operation.fail())
                return
            }
        }
        if (writeType == CBCharacteristicWriteWithResponse) {
            val event = awaitPeripheralEvent<CBPeripheralEvent.DidWriteValueForCharacteristic>(
                device.address,
                { it.characteristic.matches(characteristic) }
            ) {
                logBleCommunication(
                    direction = "tx",
                    address = operation.address,
                    deviceName = { device.name() },
                    serviceUuid = operation.characteristic.serviceUuid,
                    characteristicUuid = operation.characteristic.uuid,
                    value = operation.value,
                    details = "op=${request.id} type=with-rsp"
                )
                device.writeValue(operation.value.toNSData(), characteristic, writeType)
            }
            if (!event.result) {
                log.w {
                    "[write.failed] ${operation.logFields(request.id)} writeType=with_response"
                }
                request.result.complete(operation.fail())
                return
            }
            log.d { "[write.success] ${operation.logFields(request.id)} writeType=with_response" }
            request.result.complete(operation.success())
        } else {
            awaitCanSendWriteWithoutResponse(device)
            logBleCommunication(
                direction = "tx",
                address = operation.address,
                deviceName = { device.name() },
                serviceUuid = operation.characteristic.serviceUuid,
                characteristicUuid = operation.characteristic.uuid,
                value = operation.value,
                details = "op=${request.id} type=no-rsp"
            )
            device.writeValue(operation.value.toNSData(), characteristic, writeType)
            log.d {
                "[write.accepted] ${operation.logFields(request.id)} " +
                        "writeType=without_response acknowledgement=not_available"
            }
            request.result.complete(operation.success())
        }
    }

    private fun requestMtu(request: OperationRequest, operation: OperationType.MtuChanged) {
        val device = operation.obj as CBPeripheral
        val mtu = device.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse)
        log.i {
            "[request_mtu.success] ${operation.logFields(request.id)} payloadLength=${mtu.toInt()}"
        }
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
        log.d {
            "[write.waiting_ready] address=${device.address} name=${device.name()} writeType=without_response"
        }
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

    private suspend fun cleanupCancelledOperation(request: OperationRequest) {
        val operation = request.operation
        if (operation !is OperationType.Connect) {
            OperationManager.markRecovered(operation.address, request.id, operation.logName)
            return
        }
        val device = operation.obj as CBPeripheral
        coroutineScope {
            val disconnected = async(start = CoroutineStart.UNDISPATCHED) {
                disconnectEvents.first { it === device }
            }
            log.w {
                "[connect.recovery_disconnect] ${operation.logFields(request.id)} " +
                        "reason=cancelled_before_delivery"
            }
            centralManager.cancelPeripheralConnection(device)
            if (device.state == CBPeripheralStateDisconnected) {
                disconnected.cancel()
            } else {
                withTimeoutOrNull(RECOVERY_FALLBACK_MILLIS) { disconnected.await() }
                disconnected.cancel()
            }
        }
        OperationManager.markRecovered(operation.address, request.id, operation.logName)
    }

    private const val RECOVERY_FALLBACK_MILLIS = 1_000L
}
