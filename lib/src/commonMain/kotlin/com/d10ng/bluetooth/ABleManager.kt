package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 蓝牙管理
 * @Author d10ng
 * @Date 2025/9/29 09:58
 */
abstract class ABleManager {

    /**
     * 判断当前环境是否支持BLE
     * @return Boolean
     */
    abstract fun isSupported(): Boolean

    // 蓝牙模块开启状态
    val isEnabledFlow = MutableStateFlow(false)

    /**
     * 是否支持代码开启蓝牙
     * @return Boolean
     */
    abstract fun isSupportEnable(): Boolean

    /**
     * 开启蓝牙
     */
    abstract suspend fun enable()

    /**
     * 开始扫描
     * @param serviceUuids 需要过滤的服务UUID列表，为空时扫描所有设备
     * @return Flow<BluetoothDevice>
     */
    abstract fun scan(serviceUuids: List<String> = emptyList()): Flow<BleDevice>

    /**
     * 扫描指定地址的设备（用于重连已知设备）
     * Android 传入 MAC 地址（如 "AA:BB:CC:DD:EE:FF"）；
     * iOS 传入 CBPeripheral.identifier UUID；
     * JS/WasmJS 传入 BluetoothDevice.id。
     * @param addresses 设备地址列表
     * @return Flow<BleDevice>
     */
    abstract fun scanByAddress(addresses: List<String>): Flow<BleDevice>

    /**
     * 连接设备
     * @param device BluetoothDevice
     * @return ABluetoothConnection
     */
    abstract suspend fun connect(device: BleDevice): ABleConnection
}

/**
 * 获取平台蓝牙管理
 * @return ABleManager
 */
expect fun getPlatformBleManager(): ABleManager