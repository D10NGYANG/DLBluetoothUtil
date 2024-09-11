package com.d10ng.bluetooth

import platform.CoreBluetooth.CBPeripheral
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSError

/**
 * 蓝牙中心管理器事件
 * @Author d10ng
 * @Date 2024/9/11 16:00
 */

interface CBCentralManagerEvent {}

data class CBCentralManagerDidConnectEvent(val peripheral: CBPeripheral) : CBCentralManagerEvent
data class CBCentralManagerDidFailToConnectEvent(val peripheral: CBPeripheral, val error: NSError?) : CBCentralManagerEvent
data class CBCentralManagerDidDisconnectEvent(val peripheral: CBPeripheral, val timestamp: CFAbsoluteTime, val isReconnecting: Boolean, val error: NSError?) : CBCentralManagerEvent

