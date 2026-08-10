package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import com.d10ng.bluetooth.ABleConnection.Companion.CCC_DESCRIPTOR_UUID
import com.d10ng.bluetooth.ABleConnection.Companion.GATT_MAX_MTU_SIZE
import com.d10ng.bluetooth.ABleConnection.Companion.GATT_MIN_MTU_SIZE
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattEvent
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

/**
 * 将 [OperationManager] 请求适配为 Android `BluetoothGatt` 调用。
 *
 * 队列只有此处消费。每个请求在独立子协程中执行，使不同设备能够并发；同设备串行由
 * [OperationManager] 保证。除连接外的原生回调通过 [BleGattCallbackInstant.eventFlow] 关联回
 * 当前请求，连接结果使用独立的 `CompletableDeferred`，避免共享流丢失首次快速回调。
 */
@SuppressLint("MissingPermission")
object AndroidOperationRunner {

    internal data class ConnectedGatt(
        val gatt: BluetoothGatt,
        val callback: BleGattCallbackInstant,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    fun start() {
        log.d { "AndroidOperationRunner start" }
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
        val device = operation.obj as BluetoothDevice
        log.i {
            "[connect.start] ${operation.logFields(request.id)} name=${device.nameForLog()}"
        }
        val connectionResult = CompletableDeferred<BleGattEvent.OnConnectionStateChange>()
        val callback = BleGattCallbackInstant(
            connectionResult,
            operation.address,
            { device.nameForLog() }
        )
        val gatt = device.connectGatt(
            ctx,
            false,
            callback
        )
        var delivered = false
        try {
            val event = connectionResult.await()
            if (event.status != BluetoothGatt.GATT_SUCCESS || event.newState != BluetoothProfile.STATE_CONNECTED) {
                log.w {
                    "[connect.failed] ${operation.logFields(request.id)} " +
                            "name=${device.nameForLog()} status=${event.status} newState=${event.newState}"
                }
                request.result.complete(operation.fail())
                return
            }
            log.i {
                "[connect.success] ${operation.logFields(request.id)} name=${device.nameForLog()}"
            }
            delivered = request.result.complete(operation.success(ConnectedGatt(event.gatt, callback)))
        } finally {
            if (!delivered) {
                log.w {
                    "[connect.cleanup] ${operation.logFields(request.id)} " +
                            "reason=connection_not_delivered"
                }
                runCatching { gatt.disconnect() }.onFailure { error ->
                    log.e {
                        "[connect.cleanup_disconnect_failed] ${operation.logFields(request.id)} " +
                                "error=${error.stackTraceToString()}"
                    }
                }
                runCatching { gatt.close() }.onFailure { error ->
                    log.e {
                        "[connect.cleanup_close_failed] ${operation.logFields(request.id)} " +
                                "error=${error.stackTraceToString()}"
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun discoverServices(request: OperationRequest, operation: OperationType.DiscoverServices) {
        val gatt = operation.obj as BluetoothGatt
        val event = awaitGattEvent<BleGattEvent.OnServicesDiscovered>(gatt) {
            gatt.discoverServices()
        }
        if (event == null) return request.fail(operation, "获取服务失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w {
                "[discover_services.failed] ${operation.logFields(request.id)} status=${event.status}"
            }
            request.result.complete(operation.fail())
            return
        }
        val list = mutableListOf<BleGattService>()
        event.gatt.let { gatt ->
            gatt.services.forEach { serviceUuid ->
                gatt.getService(serviceUuid.uuid)?.let { service ->
                    val serviceItem = BleGattService(
                        service.UUIDString,
                        service.characteristics.map { characteristic ->
                            BleGattCharacteristic(
                                characteristic.UUIDString,
                                service.UUIDString,
                                BleGattCharacteristicProperty.fromValue(characteristic.properties),
                                characteristic
                            )
                        },
                        service
                    )
                    list.add(serviceItem)
                }
            }
        }
        log.i {
            "[discover_services.success] ${operation.logFields(request.id)} " +
                    "services=${list.size} characteristics=${list.sumOf { it.characteristics.size }} " +
                    "serviceDetails=${list.toServiceDiscoveryLog()}"
        }
        request.result.complete(operation.success(list))
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun notify(request: OperationRequest, operation: OperationType.Notify) {
        val gatt = operation.obj as BluetoothGatt
        val characteristic = operation.characteristic.nativeHandle as BluetoothGattCharacteristic
        val supportsNotify = operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)
        val supportsIndicate = operation.characteristic.properties.contains(BleGattCharacteristicProperty.INDICATE)
        if (!supportsNotify && !supportsIndicate) {
            log.w { "[notify.failed] ${operation.logFields(request.id)} reason=unsupported" }
            request.result.complete(operation.fail())
            return
        }
        val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
        if (descriptor == null) {
            log.w { "[notify.failed] ${operation.logFields(request.id)} reason=cccd_not_found" }
            request.result.complete(operation.fail())
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, operation.enable)) {
            log.w { "[notify.failed] ${operation.logFields(request.id)} reason=local_registration_rejected" }
            request.result.complete(operation.fail())
            return
        }
        val value = when {
            !operation.enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            supportsNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val event = awaitGattEvent<BleGattEvent.OnDescriptorWrite>(gatt, {
            it.descriptor === descriptor
        }) {
            descriptor.executeWrite(gatt, value)
        }
        if (event == null) return request.fail(operation, "设置通知失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w {
                "[notify.failed] ${operation.logFields(request.id)} " +
                        "reason=descriptor_write_failed status=${event.status}"
            }
            request.result.complete(operation.fail())
            return
        }
        log.i { "[notify.success] ${operation.logFields(request.id)}" }
        request.result.complete(operation.success())
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun write(request: OperationRequest, operation: OperationType.Write) {
        val gatt = operation.obj as BluetoothGatt
        val characteristic = operation.characteristic.nativeHandle as BluetoothGattCharacteristic
        val writeType = when {
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE) -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> {
                log.w { "[write.failed] ${operation.logFields(request.id)} reason=unsupported" }
                request.result.complete(operation.fail())
                return
            }
        }
        val event = awaitGattEvent<BleGattEvent.OnCharacteristicWrite>(gatt, {
            it.characteristic === characteristic
        }) {
            logBleCommunication(
                direction = "tx",
                address = operation.address,
                deviceName = { gatt.device.nameForLog() },
                serviceUuid = operation.characteristic.serviceUuid,
                characteristicUuid = operation.characteristic.uuid,
                value = operation.value,
                details = "op=${request.id} type=${if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) "with-rsp" else "no-rsp"}"
            )
            characteristic.executeWrite(gatt, operation.value, writeType)
        }
        if (event == null) return request.fail(operation, "写入特征失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w {
                "[write.failed] ${operation.logFields(request.id)} " +
                        "writeType=$writeType status=${event.status}"
            }
            request.result.complete(operation.fail())
            return
        }
        log.d { "[write.success] ${operation.logFields(request.id)} writeType=$writeType" }
        request.result.complete(operation.success())
    }

    private suspend fun requestMtu(request: OperationRequest, operation: OperationType.MtuChanged) {
        val gatt = operation.obj as BluetoothGatt
        val event = awaitGattEvent<BleGattEvent.OnMtuChanged>(gatt) {
            gatt.requestMtu(operation.mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE))
        }
        if (event == null) return request.fail(operation, "设置MTU失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w {
                "[request_mtu.failed] ${operation.logFields(request.id)} status=${event.status}"
            }
            request.result.complete(operation.fail())
            return
        }
        log.i {
            "[request_mtu.success] ${operation.logFields(request.id)} negotiatedMtu=${event.mtu}"
        }
        request.result.complete(operation.success(event.mtu))
    }

    private suspend inline fun <reified T : BleGattEvent> awaitGattEvent(
        gatt: BluetoothGatt,
        crossinline predicate: (T) -> Boolean = { true },
        crossinline start: () -> Boolean
    ): T? = coroutineScope {
        // UNDISPATCHED 保证先安装回调订阅，再调用可能同步失败或快速回调的原生方法。
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            BleGattCallbackInstant.first<T>(gatt, predicate)
        }
        if (!start()) {
            event.cancel()
            null
        } else {
            event.await()
        }
    }

    private fun OperationRequest.fail(operation: OperationType.DiscoverServices, message: String) {
        log.w { "[discover_services.failed] ${operation.logFields(id)} reason=$message" }
        result.complete(operation.fail())
    }

    private fun OperationRequest.fail(operation: OperationType.Notify, message: String) {
        log.w { "[notify.failed] ${operation.logFields(id)} reason=$message" }
        result.complete(operation.fail())
    }

    private fun OperationRequest.fail(operation: OperationType.Write, message: String) {
        log.w { "[write.failed] ${operation.logFields(id)} reason=$message" }
        result.complete(operation.fail())
    }

    private fun OperationRequest.fail(operation: OperationType.MtuChanged, message: String) {
        log.w { "[request_mtu.failed] ${operation.logFields(id)} reason=$message" }
        result.complete(operation.fail())
    }

    private fun cleanupCancelledOperation(request: OperationRequest) {
        // connect() 自己清理尚未交付的 GATT；已建立连接的操作取消只结束本次等待。
        OperationManager.markRecovered(request.operation.address, request.id, request.operation.logName)
    }
}
