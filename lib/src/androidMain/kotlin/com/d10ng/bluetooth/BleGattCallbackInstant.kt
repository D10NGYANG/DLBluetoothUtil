package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.d10ng.bluetooth.constant.BleGattEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 蓝牙回调实例
 * @Author d10ng
 * @Date 2025/9/29 16:28
 */
@SuppressLint("MissingPermission")
object BleGattCallbackInstant: BluetoothGattCallback() {

    val eventFlow = MutableSharedFlow<BleGattEvent>(extraBufferCapacity = Int.MAX_VALUE)

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        gatt ?: return
        log.d { "[BluetoothGattCallback.onConnectionStateChange] device: ${gatt.device.name}, status: $status, newState: $newState" }
        eventFlow.tryEmit(BleGattEvent.OnConnectionStateChange(gatt, status, newState))
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
        log.d { "[BluetoothGattCallback.onCharacteristicChanged] device: ${gatt.device.name}, characteristic: ${characteristic.uuid}, value: ${value.toHexString(HexFormat.UpperCase)}" }
        eventFlow.tryEmit(BleGattEvent.OnCharacteristicChanged(gatt, characteristic, value))
    }

    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return
        log.d { "[BluetoothGattCallback.onCharacteristicChanged] device: ${gatt.device.name}, characteristic: ${characteristic.uuid}, value: ${characteristic.value.toHexString(HexFormat.UpperCase)}" }
        eventFlow.tryEmit(BleGattEvent.OnCharacteristicChanged(gatt, characteristic, characteristic.value))
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        gatt ?: return
        log.d { "[BluetoothGattCallback.onMtuChanged] device: ${gatt.device.name}, mtu: $mtu, status: $status" }
        eventFlow.tryEmit(BleGattEvent.OnMtuChanged(gatt, mtu, status))
    }

    suspend inline fun <reified T : BleGattEvent> first(
        address: String,
        crossinline predicate: (T) -> Boolean = { true }
    ): T = eventFlow.first {
        it is T && it.gatt.device.address.contentEquals(address, true) && predicate(it)
    } as T
}