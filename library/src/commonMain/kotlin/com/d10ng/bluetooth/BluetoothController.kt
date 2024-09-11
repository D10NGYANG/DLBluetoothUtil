package com.d10ng.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 蓝牙控制器
 * @Author d10ng
 * @Date 2024/9/10 15:14
 */
object BluetoothController {

    // 已连接设备列表
    val connectedDevicesFlow = MutableStateFlow(listOf<BluetoothDevice>())
    // 扫描设备列表
    val scanDevicesFlow = MutableStateFlow(listOf<BluetoothDevice>())
    // 是否正在扫描
    val scanningFlow = MutableStateFlow(false)

    /**
     * 开始扫描
     */
    fun startScan() {
        scanDevicesFlow.value = listOf()
        cancelScan()
        scanningFlow.value = true
        BluetoothControllerMultiplatform.startScan()
    }

    /**
     * 取消扫描
     */
    fun cancelScan() {
        scanningFlow.value = false
        BluetoothControllerMultiplatform.stopScan()
    }

    /**
     * 设备扫描
     * @param device BluetoothDevice
     */
    internal fun onDeviceScan(device: BluetoothDevice) {
        val list = scanDevicesFlow.value.toMutableList()
        list.removeAll { it.address == device.address }
        list.add(device)
        scanDevicesFlow.value = list
    }

    /**
     * 连接设备
     */
    fun connect(device: BluetoothDevice) {

    }

    /**
     * 断开设备连接
     */
    fun disconnect(device: BluetoothDevice) {

    }

    /**
     * 断开所有设备连接
     */
    fun disconnectAll() {

    }
}