package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi

/**
 * Android 连接 Adapter。
 *
 * 初始化时先订阅断开和通知事件，[awaitReady] 完成后管理器才会把实例交给调用方。主动断开先
 * 更新公共状态，再等待最多 1 秒关闭 GATT；系统断开回调到达时立即关闭。两条路径由原子状态
 * 合并，保证 `disconnect()` 幂等且 `BluetoothGatt.close()` 只调用一次。
 */
internal class AndroidBleConnection(
    device: BleDevice,
    internal val gatt: BluetoothGatt,
    callback: BleGattCallbackInstant,
): ABleConnection(device) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val ready = CompletableDeferred<Unit>()
    private val closed = AtomicBoolean(false)
    private val disconnecting = AtomicBoolean(false)

    init {
        scope.launch {
            launch(start = CoroutineStart.UNDISPATCHED) {
                callback.connectionState.collect { state ->
                    if (state?.newState == BluetoothProfile.STATE_DISCONNECTED) {
                        handleDisconnected(state.status)
                    }
                }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                BleGattCallbackInstant.notificationFlow.collect {
                    if (it.gatt !== gatt) return@collect
                    val char = runCatching {
                        it.characteristic.toBleGattCharacteristic()
                    }.onFailure { error ->
                        log.e {
                            "[notification.mapping_failed] address=${device.address} " +
                                    "serviceUuid=${it.characteristic.service.uuid} " +
                                    "characteristicUuid=${it.characteristic.uuid} bytes=${it.value.size} " +
                                    "services=${mutableServicesFlow.value.size} error=${error.stackTraceToString()}"
                        }
                    }.getOrNull() ?: return@collect
                    mutableNotifyDataFlow.tryEmit(BleGattNotifyData(char, it.value))
                }
            }
            ready.complete(Unit)
        }
    }

    /** 等待内部事件订阅建立；仅由 [AndroidBleManager] 在返回连接前调用。 */
    suspend fun awaitReady() {
        ready.await()
        check(mutableIsConnectedFlow.value) { "Bluetooth disconnected before connection delivery" }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun BluetoothGattCharacteristic.toBleGattCharacteristic(): BleGattCharacteristic {
        return mutableServicesFlow.value
            .first { service -> service.uuid.contentEquals(this.service.UUIDString, true) }
            .characteristics
            .first { char -> char.uuid.contentEquals(this.UUIDString, true) }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        checkConnected()
        val result = OperationManager.execute<OperationResult.DiscoverServices>(OperationType.DiscoverServices(device.address, gatt))
            ?: operationTimedOut("discover services")
        if (result.services.isEmpty()) throw Exception("discover services failed")
        mutableServicesFlow.value = result.services
        return result.services
    }

    override suspend fun requestMaxMtu(): Int {
        checkConnected()
        val result = OperationManager.execute<OperationResult.MtuChanged>(OperationType.MtuChanged(device.address, GATT_MAX_MTU_SIZE, gatt))
        if (result == null) {
            log.w { "[request_mtu.fallback] address=${device.address} reason=timeout payloadLength=${GATT_MIN_MTU_SIZE - 3}" }
            return GATT_MIN_MTU_SIZE - 3
        }
        if (!result.result) {
            log.w { "[request_mtu.fallback] address=${device.address} reason=native_failure payloadLength=${GATT_MIN_MTU_SIZE - 3}" }
        } else {
            log.i { "[request_mtu.ready] address=${device.address} mtu=${result.mtu} payloadLength=${result.mtu - 3}" }
        }
        return (if (result.result) result.mtu else GATT_MIN_MTU_SIZE) - 3
    }

    override suspend fun write(
        characteristic: BleGattCharacteristic,
        value: ByteArray
    ) {
        checkConnected()
        val result = OperationManager.execute<OperationResult.Write>(OperationType.Write(device.address, characteristic, value, gatt))
            ?: throw Exception("write timed out")
        if (!result.result) throw Exception("write failed")
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun notify(
        characteristic: BleGattCharacteristic,
        enable: Boolean
    ) {
        checkConnected()
        val result = OperationManager.execute<OperationResult.Notify>(OperationType.Notify(device.address, characteristic, enable, gatt))
            ?: operationTimedOut("notify")
        if (!result.result) throw Exception("notify failed")
        updateNotifyStatus(characteristic, enable)
    }

    private fun checkConnected() {
        if (!mutableIsConnectedFlow.value) {
            log.w { "[connection.operation_rejected] address=${device.address} reason=disconnected" }
            error("Bluetooth connection is disconnected")
        }
    }

    private fun operationTimedOut(operation: String): Nothing {
        throw Exception("$operation timed out")
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        if (closed.get() || !disconnecting.compareAndSet(false, true)) {
            log.d { "[disconnect.ignored] address=${device.address} reason=already_disconnecting_or_closed" }
            return
        }
        log.i { "[disconnect.requested] address=${device.address} source=caller_request" }
        markDisconnected()
        runCatching { gatt.disconnect() }
            .onFailure { error ->
                log.e {
                    "[disconnect.native_failed] address=${device.address} " +
                            "source=caller_request error=${error.stackTraceToString()}"
                }
                closeGatt("disconnect_call_failed")
            }
        scope.launch {
            delay(DISCONNECT_CLOSE_TIMEOUT_MILLIS)
            closeGatt("disconnect_callback_timeout")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnected(status: Int) {
        val source = if (disconnecting.get()) "caller_request_callback" else "remote_disconnect"
        log.i {
            "[disconnect.confirmed] address=${device.address} " +
                    "source=$source status=$status"
        }
        markDisconnected()
        closeGatt("disconnected_callback")
    }

    private fun markDisconnected() {
        disconnecting.set(true)
        mutableIsConnectedFlow.value = false
        mutableServicesFlow.value = emptyList()
        mutableNotifyStatusFlow.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        log.d { "[connection.close] address=${device.address} reason=$reason" }
        runCatching { gatt.close() }.onFailure { error ->
            log.e {
                "[connection.close_failed] address=${device.address} " +
                        "reason=$reason error=${error.stackTraceToString()}"
            }
        }
        scope.cancel()
    }

    private companion object {
        const val DISCONNECT_CLOSE_TIMEOUT_MILLIS = 1_000L
    }
}
