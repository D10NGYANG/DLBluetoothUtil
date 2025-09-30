package com.d10ng.bluetooth.constant

import platform.CoreBluetooth.CBPeripheral
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSError

/**
 * 蓝牙中心管理器事件
 * @Author d10ng
 * @Date 2024/9/11 16:00
 */
sealed class CBCentralManagerEvent(
    open val peripheral: CBPeripheral
) {
    data class DidDiscoverPeripheral(override val peripheral: CBPeripheral, val name: String?, val rssi: Int) : CBCentralManagerEvent(peripheral)
    data class DidConnectResult(override val peripheral: CBPeripheral, val result: Boolean) : CBCentralManagerEvent(peripheral)
    data class DidDisconnect(override val peripheral: CBPeripheral, val timestamp: CFAbsoluteTime, val isReconnecting: Boolean, val error: NSError?) : CBCentralManagerEvent(peripheral)
}
