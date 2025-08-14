package com.d10ng.bluetooth

import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService

/**
 * 蓝牙外设事件
 * @Author d10ng
 * @Date 2024/9/11 16:45
 */
interface CBPeripheralEvent {
    val peripheral: CBPeripheral
}

data class CBPeripheralDidDiscoverServicesEvent(override val peripheral: CBPeripheral, val services: List<CBService>?) : CBPeripheralEvent
data class CBPeripheralDidDiscoverCharacteristicsForServiceEvent(override val peripheral: CBPeripheral, val service: CBService, val characteristics: List<CBCharacteristic>?) : CBPeripheralEvent
data class CBPeripheralDidWriteValueForCharacteristicEvent(override val peripheral: CBPeripheral, val result: Boolean) : CBPeripheralEvent
data class CBPeripheralIsReadyToSendWriteWithoutResponseEvent(override val peripheral: CBPeripheral) : CBPeripheralEvent