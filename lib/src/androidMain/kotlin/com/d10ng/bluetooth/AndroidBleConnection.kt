package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattEvent
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

/**
 * Android蓝牙连接
 * @Author d10ng
 * @Date 2025/9/29 15:07
 */
class AndroidBleConnection(
    device: BleDevice,
    val gatt: BluetoothGatt
): ABleConnection(device) {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            launch {
                BleGattCallbackInstant.eventFlow.collect {
                    when (it) {
                        is BleGattEvent.OnConnectionStateChange -> {
                            if (it.newState == BluetoothProfile.STATE_DISCONNECTED) handleDisconnected()
                        }
                        is BleGattEvent.OnCharacteristicChanged -> {
                            val char = runCatching { it.characteristic.toBleGattCharacteristic() }.getOrNull()?: return@collect
                            notifyDataFlow.tryEmit(BleGattNotifyData(char, it.value))
                        }
                        else -> {}
                    }
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
    private fun BluetoothGattCharacteristic.toBleGattCharacteristic(): BleGattCharacteristic {
        return servicesFlow.value
            .first { service -> service.uuid.contentEquals(this.service.UUIDString, true) }
            .characteristics
            .first { char -> char.uuid.contentEquals(this.UUIDString, true) }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        val result = OperationManager.execute<OperationResult.DiscoverServices>(OperationType.DiscoverServices(device.address, gatt))
        if (result == null || result.services.isEmpty()) throw Exception("discover services failed")
        servicesFlow.value = result.services
        return result.services
    }

    override suspend fun requestMaxMtu(): Int {
        val result = OperationManager.execute<OperationResult.MtuChanged>(OperationType.MtuChanged(device.address, GATT_MAX_MTU_SIZE, gatt))
        if (result == null || !result.result) {
            log.w { "request mtu failed" }
        } else {
            log.i { "set mtu to ${result.mtu} success" }
        }
        return (if (result?.result == true) result.mtu else GATT_MIN_MTU_SIZE) - 3
    }

    override suspend fun write(
        characteristic: BleGattCharacteristic,
        value: ByteArray
    ) {
        val result = OperationManager.execute<OperationResult.Write>(OperationType.Write(device.address, characteristic, value, gatt))
        if (result == null || !result.result) throw Exception("write failed")
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun notify(
        characteristic: BleGattCharacteristic,
        enable: Boolean
    ) {
        val result = OperationManager.execute<OperationResult.Notify>(OperationType.Notify(device.address, characteristic, enable, gatt))
        if (result == null || !result.result) throw Exception("notify failed")
        val ls = notifyStatusFlow.value.filter { it.uuid != characteristic.uuid }.toMutableList()
        if (enable) ls += characteristic
        notifyStatusFlow.value = ls
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        runCatching { gatt.close() }
        handleDisconnected()
    }

    private fun handleDisconnected() {
        isConnectedFlow.value = false
        servicesFlow.value = listOf()
        notifyStatusFlow.value = listOf()
    }
}