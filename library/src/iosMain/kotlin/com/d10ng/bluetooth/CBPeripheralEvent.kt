package com.d10ng.bluetooth

import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBService

/**
 * 蓝牙外设事件
 * @Author d10ng
 * @Date 2024/9/11 16:45
 */
interface CBPeripheralEvent {}

data class CBPeripheralDidDiscoverServicesEvent(val services: List<CBService>?) : CBPeripheralEvent
data class CBPeripheralDidDiscoverCharacteristicsForServiceEvent(val service: CBService, val characteristics: List<CBCharacteristic>?) : CBPeripheralEvent
data class CBPeripheralDidWriteValueForCharacteristicEvent(val result: Boolean) : CBPeripheralEvent
class CBPeripheralIsReadyToSendWriteWithoutResponseEvent() : CBPeripheralEvent