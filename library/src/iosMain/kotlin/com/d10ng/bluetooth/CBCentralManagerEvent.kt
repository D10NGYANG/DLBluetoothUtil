package com.d10ng.bluetooth

import platform.CoreBluetooth.CBPeripheral
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSError

/**
 * 蓝牙中心管理器事件
 * @Author d10ng
 * @Date 2024/9/11 16:00
 */

interface CBCentralManagerEvent {
    val peripheral: CBPeripheral
}

data class CBCentralManagerDidConnectEvent(override val peripheral: CBPeripheral, val mtu: Int) : CBCentralManagerEvent
data class CBCentralManagerDidFailToConnectEvent(override val peripheral: CBPeripheral, val error: NSError?) : CBCentralManagerEvent
data class CBCentralManagerDidDisconnectEvent(override val peripheral: CBPeripheral, val timestamp: CFAbsoluteTime, val isReconnecting: Boolean, val error: NSError?) : CBCentralManagerEvent

