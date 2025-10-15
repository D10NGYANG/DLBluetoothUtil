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
        log.d { "[CBCentralManagerDelegate.centralManagerDidUpdateState] state: ${state.name}" }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber
    ) {
        // 扫描结果
        val name = advertisementData["kCBAdvDataLocalName"]?.toString()?: didDiscoverPeripheral.name()
        log.d { "[CBCentralManagerDelegate.didDiscoverPeripheral] address: ${didDiscoverPeripheral.address}, name: $name, RSSI: ${RSSI.intValue}" }
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidDiscoverPeripheral(didDiscoverPeripheral, name, RSSI.intValue))
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral
    ) {
        // 连接成功
        log.d { "[CBCentralManagerDelegate.didConnectPeripheral] address: ${didConnectPeripheral.address}, name: ${didConnectPeripheral.name()}" }
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidConnectResult(didConnectPeripheral, true))
    }

    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        // 连接失败
        log.w { "[CBCentralManagerDelegate.didFailToConnectPeripheral] address: ${didFailToConnectPeripheral.address}, name: ${didFailToConnectPeripheral.name()}, error: $error" }
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
        log.i { "[CBCentralManagerDelegate.didDisconnectPeripheral] address: ${didDisconnectPeripheral.address}, name: ${didDisconnectPeripheral.name()}, timestamp: $timestamp, isReconnecting: $isReconnecting, error: $error" }
        BleCentralEvents.eventFlow.tryEmit(CBCentralManagerEvent.DidDisconnect(didDisconnectPeripheral, timestamp, isReconnecting, error))
    }
}
