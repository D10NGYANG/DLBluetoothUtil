package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
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
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return gatt.writeCharacteristic(this, payload, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
        // Fall back to deprecated version of writeCharacteristic for Android <13
        return legacyCharacteristicWrite(gatt, payload, writeType)
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.legacyCharacteristicWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
): Boolean {
    this.writeType = writeType
    value = payload
    return gatt.writeCharacteristic(this)
}


@SuppressLint("MissingPermission")
fun BluetoothGattDescriptor.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return gatt.writeDescriptor(this, payload) == BluetoothStatusCodes.SUCCESS
    } else {
        // Fall back to deprecated version of writeDescriptor for Android <13
        return legacyDescriptorWrite(gatt, payload)
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.legacyDescriptorWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
): Boolean {
    value = payload
    return gatt.writeDescriptor(this)
}
