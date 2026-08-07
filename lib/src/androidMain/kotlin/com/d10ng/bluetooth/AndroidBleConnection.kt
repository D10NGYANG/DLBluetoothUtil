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
    internal val gatt: BluetoothGatt
): ABleConnection(device) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val ready = CompletableDeferred<Unit>()
    private val closed = AtomicBoolean(false)
    private val disconnecting = AtomicBoolean(false)

    init {
        scope.launch {
            launch(start = CoroutineStart.UNDISPATCHED) {
                BleGattCallbackInstant.eventFlow.collect {
                    if (it.gatt !== gatt) return@collect
                    if (it is BleGattEvent.OnConnectionStateChange &&
                        it.newState == BluetoothProfile.STATE_DISCONNECTED
                    ) {
                        handleDisconnected()
                    }
                }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                BleGattCallbackInstant.notificationFlow.collect {
                    if (it.gatt !== gatt) return@collect
                    val char = runCatching {
                        it.characteristic.toBleGattCharacteristic()
                    }.getOrNull() ?: return@collect
                    mutableNotifyDataFlow.tryEmit(BleGattNotifyData(char, it.value))
                }
            }
            ready.complete(Unit)
        }
    }

    /** 等待内部事件订阅建立；仅由 [AndroidBleManager] 在返回连接前调用。 */
    suspend fun awaitReady() = ready.await()

    @OptIn(ExperimentalUuidApi::class)
    private fun BluetoothGattCharacteristic.toBleGattCharacteristic(): BleGattCharacteristic {
        return mutableServicesFlow.value
            .first { service -> service.uuid.contentEquals(this.service.UUIDString, true) }
            .characteristics
            .first { char -> char.uuid.contentEquals(this.UUIDString, true) }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        val result = OperationManager.execute<OperationResult.DiscoverServices>(OperationType.DiscoverServices(device.address, gatt))
        if (result == null || result.services.isEmpty()) throw Exception("discover services failed")
        mutableServicesFlow.value = result.services
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
        updateNotifyStatus(characteristic, enable)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        if (closed.get() || !disconnecting.compareAndSet(false, true)) return
        markDisconnected()
        runCatching { gatt.disconnect() }
            .onFailure { closeGatt() }
        scope.launch {
            delay(DISCONNECT_CLOSE_TIMEOUT_MILLIS)
            closeGatt()
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnected() {
        markDisconnected()
        closeGatt()
    }

    private fun markDisconnected() {
        disconnecting.set(true)
        mutableIsConnectedFlow.value = false
        mutableServicesFlow.value = emptyList()
        mutableNotifyStatusFlow.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { gatt.close() }
        scope.cancel()
    }

    private companion object {
        const val DISCONNECT_CLOSE_TIMEOUT_MILLIS = 1_000L
    }
}
