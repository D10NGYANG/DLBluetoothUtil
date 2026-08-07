package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattService

/** 返回该设备对应的 Web Bluetooth `BluetoothDevice` 动态对象。 */
@ExperimentalNativeBleApi
fun BleDevice.webBluetoothDevice(): dynamic = nativeHandle.asDynamic()

/** 返回服务发现时保存的 Web Bluetooth `BluetoothRemoteGATTService` 动态对象。 */
@ExperimentalNativeBleApi
fun BleGattService.webBluetoothService(): dynamic = nativeHandle.asDynamic()

/** 返回服务发现时保存的 Web Bluetooth `BluetoothRemoteGATTCharacteristic` 动态对象。 */
@ExperimentalNativeBleApi
fun BleGattCharacteristic.webBluetoothCharacteristic(): dynamic = nativeHandle.asDynamic()

/**
 * 返回当前 JS 连接持有的 `BluetoothRemoteGATTServer` 动态对象。
 *
 * 接收者不是 JS Web 实现时结果为 `null`。不要移除本库注册的事件监听器或主动关闭连接。
 */
@ExperimentalNativeBleApi
fun ABleConnection.webBluetoothGattOrNull(): dynamic =
    (this as? WebBleConnection)?.gatt
