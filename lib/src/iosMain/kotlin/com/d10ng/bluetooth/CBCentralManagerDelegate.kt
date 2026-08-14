package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBManagerStateEnum
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheral
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject

/**
 * BLE 中心管理代理
 * @Author d10ng
 * @Date 2025/9/30 13:49
 */
internal val CBCentralManagerDelegate: CBCentralManagerDelegateProtocol = object : NSObject(), CBCentralManagerDelegateProtocol {

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        // 状态更新
        val state = CBManagerStateEnum.from(central.state)
        BleCentralEvents.stateFlow.value = state
        log.i { "[bluetooth.state_changed] state=${state.name}" }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber
    ) {
        // 扫描结果
        val name = advertisementData["kCBAdvDataLocalName"]?.toString()?: didDiscoverPeripheral.name()
        log.d { "[scan.result] address=${didDiscoverPeripheral.address} name=$name rssi=${RSSI.intValue}" }
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidDiscoverPeripheral(didDiscoverPeripheral, name, RSSI.intValue))
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral
    ) {
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidConnectResult(didConnectPeripheral, true))
    }

    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        // 连接失败
        log.w { "[connect.callback] address=${didFailToConnectPeripheral.address} name=${didFailToConnectPeripheral.name()} result=failed error=$error" }
        IosOperationRunner.onPeripheralDisconnected(didFailToConnectPeripheral)
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidConnectResult(didFailToConnectPeripheral, false))
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        timestamp: CFAbsoluteTime,
        isReconnecting: Boolean,
        error: NSError?
    ) {
        // 断开连接
        log.i { "[disconnect.callback] address=${didDisconnectPeripheral.address} name=${didDisconnectPeripheral.name()} timestamp=$timestamp isReconnecting=$isReconnecting error=$error" }
        IosOperationRunner.onPeripheralDisconnected(didDisconnectPeripheral)
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidDisconnect(didDisconnectPeripheral, timestamp, isReconnecting, error))
    }
}
