package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 蓝牙连接
 * @Author d10ng
 * @Date 2025/9/29 09:34
 */
abstract class ABleConnection(
    val device: BleDevice
) {

    companion object {
        const val GATT_MAX_MTU_SIZE = 517
        const val GATT_MIN_MTU_SIZE = 23
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"
    }

    // 连接状态
    val isConnectedFlow = MutableStateFlow(true)

    // 服务列表
    val servicesFlow = MutableStateFlow<List<BleGattService>>(listOf())

    // 订阅通知状态
    val notifyStatusFlow = MutableStateFlow<List<BleGattCharacteristic>>(listOf())

    // 通知数据
    val notifyDataFlow = MutableSharedFlow<BleGattNotifyData>(extraBufferCapacity = Int.MAX_VALUE)

    /**
     * 发现服务
     * @return List<BluetoothGattService> 服务列表
     */
    abstract suspend fun discoverServices(): List<BleGattService>

    /**
     * 请求最大写入MTU
     * @return Int 设备支持的最大MTU
     */
    abstract suspend fun requestMaxMtu(): Int

    /**
     * 写入特征值
     * @param characteristic BleGattCharacteristic 特征值
     * @param value ByteArray 数据
     */
    abstract suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray)

    /**
     * 监听特征值
     * @param characteristic BleGattCharacteristic 特征值
     * @param enable Boolean 是否开启监听
     */
    abstract suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean)

    /**
     * 断开连接
     */
    abstract fun disconnect()
}