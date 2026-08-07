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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    fun start() {
        log.d { "AndroidOperationRunner start" }
    }

    init {
        scope.launch {
            for (request in OperationManager.queueChannel) {
                val operation = request.operation
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
                    if (request.result.isCancelled) job.cancel()
                }
                job.start()
            }
        }
    }

    private suspend fun connect(request: OperationRequest, operation: OperationType.Connect) {
        val device = operation.obj as BluetoothDevice
        val connectionResult = CompletableDeferred<BleGattEvent.OnConnectionStateChange>()
        val gatt = device.connectGatt(ctx, false, BleGattCallbackInstant(connectionResult))
        var delivered = false
        try {
            val event = connectionResult.await()
            if (event.status != BluetoothGatt.GATT_SUCCESS || event.newState != BluetoothProfile.STATE_CONNECTED) {
                log.w { "[OperationType.Connect] fail 连接失败, status=${event.status}, newState=${event.newState}" }
                request.result.complete(operation.fail())
                return
            }
            log.d { "[OperationType.Connect] success 连接成功" }
            delivered = request.result.complete(operation.success(event.gatt))
        } finally {
            if (!delivered) {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun discoverServices(request: OperationRequest, operation: OperationType.DiscoverServices) {
        val gatt = operation.obj as BluetoothGatt
        val event = awaitGattEvent<BleGattEvent.OnServicesDiscovered>(operation.address, gatt) {
            gatt.discoverServices()
        }
        if (event == null) return request.fail(operation, "获取服务失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.DiscoverServices] fail 获取服务失败" }
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
        log.i { "[OperationType.DiscoverServices] success 获取服务成功" }
        request.result.complete(operation.success(list))
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun notify(request: OperationRequest, operation: OperationType.Notify) {
        val gatt = operation.obj as BluetoothGatt
        val characteristic = operation.characteristic.nativeHandle as BluetoothGattCharacteristic
        val supportsNotify = operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)
        val supportsIndicate = operation.characteristic.properties.contains(BleGattCharacteristicProperty.INDICATE)
        if (!supportsNotify && !supportsIndicate) {
            log.w { "[OperationType.Notify] fail 特征不支持通知" }
            request.result.complete(operation.fail())
            return
        }
        val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
        if (descriptor == null) {
            log.w { "[OperationType.Notify] fail 未找到描述符" }
            request.result.complete(operation.fail())
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, operation.enable)) {
            log.w { "[OperationType.Notify] fail 设置通知失败" }
            request.result.complete(operation.fail())
            return
        }
        val value = when {
            !operation.enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            supportsNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val event = awaitGattEvent<BleGattEvent.OnDescriptorWrite>(operation.address, gatt, {
            it.descriptor === descriptor
        }) {
            descriptor.executeWrite(gatt, value)
        }
        if (event == null) return request.fail(operation, "设置通知失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.Notify] fail 设置通知失败" }
            request.result.complete(operation.fail())
            return
        }
        log.d { "[OperationType.Notify] success 设置通知成功" }
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
                log.w { "[OperationType.Write] fail 特征不支持写入" }
                request.result.complete(operation.fail())
                return
            }
        }
        val event = awaitGattEvent<BleGattEvent.OnCharacteristicWrite>(operation.address, gatt, {
            it.characteristic === characteristic
        }) {
            characteristic.executeWrite(gatt, operation.value, writeType)
        }
        if (event == null) return request.fail(operation, "写入特征失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.Write] fail 写入特征失败" }
            request.result.complete(operation.fail())
            return
        }
        log.d { "[OperationType.Write] success 写入特征成功" }
        request.result.complete(operation.success())
    }

    private suspend fun requestMtu(request: OperationRequest, operation: OperationType.MtuChanged) {
        val gatt = operation.obj as BluetoothGatt
        val event = awaitGattEvent<BleGattEvent.OnMtuChanged>(operation.address, gatt) {
            gatt.requestMtu(operation.mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE))
        }
        if (event == null) return request.fail(operation, "设置MTU失败")
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.MtuChanged] fail 设置MTU失败" }
            request.result.complete(operation.fail())
            return
        }
        log.d { "[OperationType.MtuChanged] success 设置MTU成功" }
        request.result.complete(operation.success(event.mtu))
    }

    private suspend inline fun <reified T : BleGattEvent> awaitGattEvent(
        address: String,
        gatt: BluetoothGatt,
        crossinline predicate: (T) -> Boolean = { true },
        crossinline start: () -> Boolean
    ): T? = coroutineScope {
        // UNDISPATCHED 保证先安装回调订阅，再调用可能同步失败或快速回调的原生方法。
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            BleGattCallbackInstant.first<T>(address) { it.gatt === gatt && predicate(it) }
        }
        if (!start()) {
            event.cancel()
            null
        } else {
            event.await()
        }
    }

    private fun OperationRequest.fail(operation: OperationType.DiscoverServices, message: String) {
        log.w { "[OperationType.DiscoverServices] fail $message" }
        result.complete(operation.fail())
    }

    private fun OperationRequest.fail(operation: OperationType.Notify, message: String) {
        log.w { "[OperationType.Notify] fail $message" }
        result.complete(operation.fail())
    }

    private fun OperationRequest.fail(operation: OperationType.Write, message: String) {
        log.w { "[OperationType.Write] fail $message" }
        result.complete(operation.fail())
    }

    private fun OperationRequest.fail(operation: OperationType.MtuChanged, message: String) {
        log.w { "[OperationType.MtuChanged] fail $message" }
        result.complete(operation.fail())
    }
}
