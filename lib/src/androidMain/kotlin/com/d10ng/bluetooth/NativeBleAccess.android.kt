package com.d10ng.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattService

/** 返回该设备对应的 Android 原生对象。返回值仍由本库持有，不应关闭或替换其回调。 */
@ExperimentalNativeBleApi
fun BleDevice.androidBluetoothDevice(): BluetoothDevice = nativeHandle as BluetoothDevice

/** 返回服务发现时保存的 Android 原生 GATT 服务。 */
@ExperimentalNativeBleApi
fun BleGattService.androidBluetoothGattService(): BluetoothGattService =
    nativeHandle as BluetoothGattService

/** 返回服务发现时保存的 Android 原生 GATT 特征。 */
@ExperimentalNativeBleApi
fun BleGattCharacteristic.androidBluetoothGattCharacteristic(): BluetoothGattCharacteristic =
    nativeHandle as BluetoothGattCharacteristic

/**
 * 返回当前 Android 连接持有的 GATT；接收者不是 Android 实现时返回 `null`。
 *
 * 直接发起 GATT 操作不会经过本库的串行队列。不要调用 `close()`、替换 callback，或与
 * [ABleConnection] 的挂起操作并发使用。
 */
@ExperimentalNativeBleApi
fun ABleConnection.androidBluetoothGattOrNull(): BluetoothGatt? =
    (this as? AndroidBleConnection)?.gatt
