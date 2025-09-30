package com.d10ng.bluetooth.constant

import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService

/**
 * 蓝牙外设事件
 * @Author d10ng
 * @Date 2024/9/11 16:45
 */
sealed class CBPeripheralEvent (
    open val peripheral: CBPeripheral
) {
    data class DidDiscoverServices(override val peripheral: CBPeripheral, val services: List<CBService>?) : CBPeripheralEvent(peripheral)
    data class DidDiscoverCharacteristicsForService(override val peripheral: CBPeripheral, val service: CBService, val characteristics: List<CBCharacteristic>?) : CBPeripheralEvent(peripheral)
    class DidUpdateValueForCharacteristic(override val peripheral: CBPeripheral, val characteristic: CBCharacteristic, data: ByteArray) : CBPeripheralEvent(peripheral)
    data class DidWriteValueForCharacteristic(override val peripheral: CBPeripheral, val characteristic: CBCharacteristic, val result: Boolean) : CBPeripheralEvent(peripheral)
    data class DidWriteValueForDescriptor(override val peripheral: CBPeripheral, val descriptor: CBDescriptor, val result: Boolean) : CBPeripheralEvent(peripheral)
    data class IsReadyToSendWriteWithoutResponse(override val peripheral: CBPeripheral) : CBPeripheralEvent(peripheral)
}