package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.d10ng.bluetooth.constant.BleGattEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Android `BluetoothGattCallback` 到协程事件的适配器。
 *
 * 控制回调进入 [eventFlow]，供一次性操作等待；高频通知只进入 [notificationFlow]，由对应连接
 * 转发。两类事件分流可避免业务通知抢占控制事件缓冲，导致写入、服务发现或 MTU 请求超时。
 * 连接首次结果另行完成 [connectionResult]，确保在共享流尚无订阅者时也不会丢失。
 */
@SuppressLint("MissingPermission")
internal class BleGattCallbackInstant(
    private val connectionResult: CompletableDeferred<BleGattEvent.OnConnectionStateChange>
) : BluetoothGattCallback() {

    companion object {
        val eventFlow = MutableSharedFlow<BleGattEvent>(
            extraBufferCapacity = EVENT_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val notificationFlow = MutableSharedFlow<BleGattEvent.OnCharacteristicChanged>(
            extraBufferCapacity = EVENT_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        private const val EVENT_BUFFER_CAPACITY = 64

        suspend inline fun <reified T : BleGattEvent> first(
            address: String,
            crossinline predicate: (T) -> Boolean = { true }
        ): T = eventFlow.first {
            it is T && it.gatt.device.address.contentEquals(address, true) && predicate(it)
        } as T
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        gatt ?: return
        log.d { "[BluetoothGattCallback.onConnectionStateChange] device: ${gatt.device.name}, status: $status, newState: $newState" }
        val event = BleGattEvent.OnConnectionStateChange(gatt, status, newState)
        connectionResult.complete(event)
        eventFlow.tryEmit(event)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        gatt ?: return
        log.d { "[BluetoothGattCallback.onServicesDiscovered] device: ${gatt.device.name}, status: $status" }
        eventFlow.tryEmit(BleGattEvent.OnServicesDiscovered(gatt, status))
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return
        log.d { "[BluetoothGattCallback.onCharacteristicWrite] device: ${gatt.device.name}, characteristic: ${characteristic.uuid}, status: $status" }
        eventFlow.tryEmit(BleGattEvent.OnCharacteristicWrite(gatt, characteristic, status))
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        gatt ?: return
        descriptor ?: return
        log.d { "[BluetoothGattCallback.onDescriptorWrite] device: ${gatt.device.name}, descriptor: ${descriptor.uuid}, status: $status" }
        eventFlow.tryEmit(BleGattEvent.OnDescriptorWrite(gatt, descriptor, status))
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        log.d { "[BluetoothGattCallback.onCharacteristicChanged] characteristic: ${characteristic.uuid}, bytes: ${value.size}" }
        notificationFlow.tryEmit(BleGattEvent.OnCharacteristicChanged(gatt, characteristic, value))
    }

    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return
        val value = characteristic.value ?: return
        log.d { "[BluetoothGattCallback.onCharacteristicChanged] characteristic: ${characteristic.uuid}, bytes: ${value.size}" }
        notificationFlow.tryEmit(BleGattEvent.OnCharacteristicChanged(gatt, characteristic, value))
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        gatt ?: return
        log.d { "[BluetoothGattCallback.onMtuChanged] device: ${gatt.device.name}, mtu: $mtu, status: $status" }
        eventFlow.tryEmit(BleGattEvent.OnMtuChanged(gatt, mtu, status))
    }
}
