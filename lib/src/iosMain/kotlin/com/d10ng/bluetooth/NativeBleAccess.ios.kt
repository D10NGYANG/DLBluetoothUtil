package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattService
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService

/** 返回该设备对应的 CoreBluetooth peripheral。不要替换其 delegate。 */
@ExperimentalNativeBleApi
fun BleDevice.iosPeripheral(): CBPeripheral = nativeHandle as CBPeripheral

/** 返回服务发现时保存的 CoreBluetooth service。 */
@ExperimentalNativeBleApi
fun BleGattService.iosService(): CBService = nativeHandle as CBService

/** 返回服务发现时保存的 CoreBluetooth characteristic。 */
@ExperimentalNativeBleApi
fun BleGattCharacteristic.iosCharacteristic(): CBCharacteristic =
    nativeHandle as CBCharacteristic

/**
 * 返回当前 iOS 连接持有的 peripheral；接收者不是 iOS 实现时返回 `null`。
 *
 * 直接发起 CoreBluetooth 操作不会经过本库的串行队列。不要替换 delegate，或与
 * [ABleConnection] 的挂起操作并发使用。
 */
@ExperimentalNativeBleApi
fun ABleConnection.iosPeripheralOrNull(): CBPeripheral? =
    (this as? IosBleConnection)?.peripheral
