package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BluetoothDevice
import com.d10ng.bluetooth.constant.BluetoothGattCharacteristic
import com.d10ng.bluetooth.constant.BluetoothGattNotifyData
import com.d10ng.bluetooth.constant.BluetoothGattService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 蓝牙连接
 * @Author d10ng
 * @Date 2025/9/29 09:34
 */
abstract class ABluetoothConnection(
    val device: BluetoothDevice
) {

    // 连接状态
    val isConnectedFlow = MutableStateFlow(true)

    // 服务列表
    val servicesFlow = MutableStateFlow<List<BluetoothGattService>>(listOf())

    // 订阅通知状态
    val notifyStatusFlow = MutableStateFlow<List<BluetoothGattCharacteristic>>(listOf())

    // 通知数据
    val notifyDataFlow = MutableSharedFlow<BluetoothGattNotifyData>(extraBufferCapacity = Int.MAX_VALUE)

    /**
     * 发现服务
     * @return List<BluetoothGattService> 服务列表
     */
    abstract suspend fun discoverServices(): List<BluetoothGattService>

    /**
     * 请求最大写入MTU
     * @return Int 设备支持的最大MTU
     */
    abstract suspend fun requestMaxMtu(): Int

    /**
     * 写入特征值
     * @param serviceUuid String 服务UUID
     * @param characteristicUuid String 特征值UUID
     * @param value ByteArray 数据
     */
    abstract suspend fun write(serviceUuid: String, characteristicUuid: String, value: ByteArray)

    /**
     * 监听特征值
     * @param serviceUuid String 服务UUID
     * @param characteristicUuid String 特征值UUID
     * @param enable Boolean 是否开启监听
     */
    abstract suspend fun notify(serviceUuid: String, characteristicUuid: String, enable: Boolean)

    /**
     * 断开连接
     */
    abstract suspend fun disconnect()
}