package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattService

/** 返回该设备对应的 Web Bluetooth 原生设备对象。 */
@ExperimentalNativeBleApi
@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun BleDevice.webBluetoothDevice(): BluetoothDevice = nativeHandle as BluetoothDevice

/** 返回服务发现时保存的 Web Bluetooth GATT 服务。 */
@ExperimentalNativeBleApi
@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun BleGattService.webBluetoothService(): BluetoothRemoteGATTService =
    nativeHandle as BluetoothRemoteGATTService

/** 返回服务发现时保存的 Web Bluetooth GATT 特征。 */
@ExperimentalNativeBleApi
@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun BleGattCharacteristic.webBluetoothCharacteristic(): BluetoothRemoteGATTCharacteristic =
    nativeHandle as BluetoothRemoteGATTCharacteristic

/**
 * 返回当前 WasmJS 连接持有的 GATT server；接收者不是 WasmJS Web 实现时返回 `null`。
 * 不要移除本库注册的事件监听器或主动关闭连接。
 */
@ExperimentalNativeBleApi
fun ABleConnection.webBluetoothGattOrNull(): BluetoothRemoteGATTServer? =
    (this as? WebBleConnection)?.gatt
