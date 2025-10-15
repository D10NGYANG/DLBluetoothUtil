package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build

/**
 * 蓝牙工具类
 * @Author d10ng
 * @Date 2025/9/29 17:09
 */

val BluetoothGattService.UUIDString
    get() = this.uuid.toString()

val BluetoothGattCharacteristic.UUIDString
    get() = this.uuid.toString()

@SuppressLint("MissingPermission")
fun BluetoothGattCharacteristic.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(this, payload, writeType)
    } else {
        // Fall back to deprecated version of writeCharacteristic for Android <13
        legacyCharacteristicWrite(gatt, payload, writeType)
    }
}

@TargetApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.legacyCharacteristicWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
) {
    this.writeType = writeType
    value = payload
    gatt.writeCharacteristic(this)
}


@SuppressLint("MissingPermission")
fun BluetoothGattDescriptor.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(this, payload)
    } else {
        // Fall back to deprecated version of writeDescriptor for Android <13
        legacyDescriptorWrite(gatt, payload)
    }
}

@TargetApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.legacyDescriptorWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
) {
    value = payload
    gatt.writeDescriptor(this)
}
