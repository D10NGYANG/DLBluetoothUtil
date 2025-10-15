package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.d10ng.bluetooth.ABleConnection.Companion.CCC_DESCRIPTOR_UUID
import com.d10ng.bluetooth.ABleConnection.Companion.GATT_MAX_MTU_SIZE
import com.d10ng.bluetooth.ABleConnection.Companion.GATT_MIN_MTU_SIZE
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattEvent
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

/**
 * Android操作执行器
 * @Author d10ng
 * @Date 2025/9/29 16:41
 */
@SuppressLint("MissingPermission")
object AndroidOperationRunner {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        log.d { "AndroidOperationRunner start" }
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
        val device = operation.obj as BluetoothDevice
        device.connectGatt(ctx, false, BleGattCallbackInstant)
        val event = BleGattCallbackInstant.first<BleGattEvent.OnConnectionStateChange>(operation.address)
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.Connect] fail 连接失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        log.d { "[OperationType.Connect] success 连接成功" }
        OperationManager.resultFlow.tryEmit(operation.success(event.gatt))
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun discoverServices(operation: OperationType.DiscoverServices) {
        val gatt = operation.obj as BluetoothGatt
        if (!gatt.discoverServices()) {
            log.w { "[OperationType.DiscoverServices] fail 获取服务失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        val event = BleGattCallbackInstant.first<BleGattEvent.OnServicesDiscovered>(operation.address)
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.DiscoverServices] fail 获取服务失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
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
        log.w { "[OperationType.DiscoverServices] success 获取服务成功" }
        OperationManager.resultFlow.tryEmit(operation.success(list))
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun notify(operation: OperationType.Notify) {
        val gatt = operation.obj as BluetoothGatt
        val characteristic = operation.characteristic.obj as BluetoothGattCharacteristic
        if (!operation.characteristic.properties.contains(BleGattCharacteristicProperty.NOTIFY)) {
            log.w { "[OperationType.Notify] fail 特征不支持通知" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
        if (descriptor == null) {
            log.w { "[OperationType.Notify] fail 未找到描述符" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, operation.enable)) {
            log.w { "[OperationType.Notify] fail 设置通知失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        val value = if (operation.enable)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        descriptor.executeWrite(gatt, value)
        val event = BleGattCallbackInstant.first<BleGattEvent.OnDescriptorWrite>(operation.address) {
            it.descriptor.uuid == descriptor.uuid
        }
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.Notify] fail 设置通知失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        log.d { "[OperationType.Notify] success 设置通知成功" }
        OperationManager.resultFlow.tryEmit(operation.success())
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun write(operation: OperationType.Write) {
        val gatt = operation.obj as BluetoothGatt
        val characteristic = operation.characteristic.obj as BluetoothGattCharacteristic
        val writeType = when {
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE) -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            operation.characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE) -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> {
                log.w { "[OperationType.Write] fail 特征不支持写入" }
                OperationManager.resultFlow.tryEmit(operation.fail())
                return
            }
        }
        characteristic.executeWrite(gatt, operation.value, writeType)
        val event = BleGattCallbackInstant.first<BleGattEvent.OnCharacteristicWrite>(operation.address) {
            it.characteristic.uuid == characteristic.uuid
        }
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.Write] fail 写入特征失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        log.d { "[OperationType.Write] success 写入特征成功" }
        OperationManager.resultFlow.tryEmit(operation.success())
    }

    private suspend fun requestMtu(operation: OperationType.MtuChanged) {
        val gatt = operation.obj as BluetoothGatt
        if (!gatt.requestMtu(operation.mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE))) {
            log.w { "[OperationType.MtuChanged] fail 设置MTU失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        val event = BleGattCallbackInstant.first<BleGattEvent.OnMtuChanged>(operation.address)
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            log.w { "[OperationType.MtuChanged] fail 设置MTU失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }
        log.d { "[OperationType.MtuChanged] success 设置MTU成功" }
        OperationManager.resultFlow.tryEmit(operation.success(event.mtu))
    }
}