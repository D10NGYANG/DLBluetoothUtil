package com.d10ng.bluetooth

import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService

/**
 * CBPeripheral扩展
 * @Author d10ng
 * @Date 2025/8/13 18:01
 */

val CBPeripheral.address
    get() = identifier.UUIDString

val CBService.serviceUuid
    get() = UUID.UUIDString

val CBCharacteristic.serviceUuid
    get() = service!!.UUID.UUIDString

val CBCharacteristic.characteristicUuid
    get() = UUID.UUIDString
