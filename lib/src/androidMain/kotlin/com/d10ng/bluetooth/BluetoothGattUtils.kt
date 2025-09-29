package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import java.util.UUID

/**
 * 蓝牙工具类
 * @Author d10ng
 * @Date 2025/9/29 17:09
 */


/**
 * 查找特征
 * @receiver BluetoothGatt
 * @param characteristicUuid 特征UUID
 * @param serviceUuid 服务UUID
 * @return BluetoothGattCharacteristic?
 */
fun BluetoothGatt.findCharacteristic(
    characteristicUuid: String,
    serviceUuid: String? = null
): BluetoothGattCharacteristic? {
    val sUuid = UUID.fromString(serviceUuid)
    val cUuid = UUID.fromString(characteristicUuid)
    return if (serviceUuid != null) {
        // If serviceUuid is available, use it to disambiguate cases where multiple services have
        // distinct characteristics that happen to use the same UUID
        services
            ?.firstOrNull { it.uuid == sUuid }
            ?.characteristics?.firstOrNull { it.uuid == cUuid }
    } else {
        // Iterate through services and find the first one with a match for the characteristic UUID
        services?.forEach { service ->
            service.characteristics?.firstOrNull { characteristic ->
                characteristic.uuid == cUuid
            }?.let { matchingCharacteristic ->
                return matchingCharacteristic
            }
        }
        return null
    }
}

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
    properties and property != 0

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
