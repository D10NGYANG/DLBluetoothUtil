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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val connectionResult: CompletableDeferred<BleGattEvent.OnConnectionStateChange>,
    private val deviceAddress: String,
    private val deviceName: () -> String?,
) : BluetoothGattCallback() {

    private val mutableConnectionState =
        MutableStateFlow<BleGattEvent.OnConnectionStateChange?>(null)
    val connectionState: StateFlow<BleGattEvent.OnConnectionStateChange?> =
        mutableConnectionState.asStateFlow()

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
            gatt: BluetoothGatt,
            crossinline predicate: (T) -> Boolean = { true }
        ): T = eventFlow.first {
            it is T && it.gatt === gatt && predicate(it)
        } as T
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (gatt == null) {
            log.w { "[gatt.connection_state] error=gatt_is_null status=$status newState=$newState" }
            return
        }
        val event = BleGattEvent.OnConnectionStateChange(gatt, status, newState)
        mutableConnectionState.value = event
        connectionResult.complete(event)
        eventFlow.tryEmit(event)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (gatt == null) {
            log.w { "[gatt.services_discovered] error=gatt_is_null status=$status" }
            return
        }
        eventFlow.tryEmit(BleGattEvent.OnServicesDiscovered(gatt, status))
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        if (gatt == null || characteristic == null) {
            log.w { "[gatt.characteristic_write] error=null_callback_argument status=$status" }
            return
        }
        eventFlow.tryEmit(BleGattEvent.OnCharacteristicWrite(gatt, characteristic, status))
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        if (gatt == null || descriptor == null) {
            log.w { "[gatt.descriptor_write] error=null_callback_argument status=$status" }
            return
        }
        eventFlow.tryEmit(BleGattEvent.OnDescriptorWrite(gatt, descriptor, status))
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        logBleCommunication(
            direction = "rx",
            address = deviceAddress,
            deviceName = deviceName,
            serviceUuid = characteristic.service.uuid.toString(),
            characteristicUuid = characteristic.uuid.toString(),
            value = value
        )
        notificationFlow.tryEmit(BleGattEvent.OnCharacteristicChanged(gatt, characteristic, value))
    }

    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        if (gatt == null || characteristic == null) {
            log.w { "[ble.rx] error=null_callback_argument" }
            return
        }
        val value = characteristic.value
        if (value == null) {
            log.w {
                "[ble.rx] address=$deviceAddress " +
                        "serviceUuid=${characteristic.service.uuid} characteristicUuid=${characteristic.uuid} error=value_is_null"
            }
            return
        }
        logBleCommunication(
            direction = "rx",
            address = deviceAddress,
            deviceName = deviceName,
            serviceUuid = characteristic.service.uuid.toString(),
            characteristicUuid = characteristic.uuid.toString(),
            value = value
        )
        notificationFlow.tryEmit(BleGattEvent.OnCharacteristicChanged(gatt, characteristic, value))
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (gatt == null) {
            log.w { "[gatt.mtu_changed] error=gatt_is_null mtu=$mtu status=$status" }
            return
        }
        eventFlow.tryEmit(BleGattEvent.OnMtuChanged(gatt, mtu, status))
    }
}
