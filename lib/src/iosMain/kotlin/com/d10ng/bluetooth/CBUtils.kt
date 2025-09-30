package com.d10ng.bluetooth

import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * CB 扩展
 * @Author d10ng
 * @Date 2025/8/13 18:01
 */

val CBPeripheral.address
    get() = identifier.UUIDString

@OptIn(ExperimentalUuidApi::class)
val CBService.Uuid
    get() = UUID.toUuid()

val CBCharacteristic.serviceUUIDString
    get() = service!!.UUID.UUIDString

val CBCharacteristic.UUIDString
    get() = UUID.UUIDString

@OptIn(ExperimentalUuidApi::class)
val CBCharacteristic.Uuid
    get() = UUID.toUuid()

@OptIn(ExperimentalUuidApi::class)
fun CBUUID.toUuid(): Uuid {
    return Uuid.parse(UUIDString)
}
