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
     * @return Flow<BluetoothDevice>
     */
    abstract fun scan(): Flow<BleDevice>

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