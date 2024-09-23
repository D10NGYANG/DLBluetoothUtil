package com.d10ng.bluetooth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 蓝牙控制器
 * @Author d10ng
 * @Date 2024/9/10 15:14
 */
object BluetoothController {

    private val controller = getBluetoothController()
    // 已连接设备列表
    val connectedDevicesFlow = MutableStateFlow(listOf<BluetoothDevice>())
    // 扫描设备列表
    val scanDevicesFlow = MutableStateFlow(listOf<BluetoothDevice>())
    // 是否正在扫描
    val scanningFlow = MutableStateFlow(false)
    // 通知数据
    val notifyDataFlow = MutableSharedFlow<Pair<String, ByteArray>>()
    // 数据分包传输大小
    var splitWriteNum = 20

    /**
     * 设置日志输出
     * @param debug Boolean
     */
    fun setDebug(debug: Boolean) {
        Logger.debug = debug
    }

    /**
     * 开始扫描
     */
    fun startScan() {
        scanDevicesFlow.value = listOf()
        cancelScan()
        scanningFlow.value = true
        controller.startScan()
    }

    /**
     * 取消扫描
     */
    fun cancelScan() {
        scanningFlow.value = false
        controller.stopScan()
    }

    /**
     * 设备扫描
     * @param device BluetoothDevice
     */
    internal fun onDeviceScan(device: BluetoothDevice) {
        if (device.name == null) return
        val list = scanDevicesFlow.value.toMutableList()
        list.removeAll { it.address == device.address }
        list.add(device)
        scanDevicesFlow.value = list.apply { sortByDescending { it.rssi } }
    }

    /**
     * 连接设备
     * @param device BluetoothDevice
     * @return List<BluetoothGattService>
     */
    suspend fun connect(device: BluetoothDevice): List<BluetoothGattService> {
        cancelScan()
        val list = controller.connect(device.address)
        val devices = connectedDevicesFlow.value.toMutableList()
        devices.removeAll { it.address == device.address }
        devices.add(device)
        connectedDevicesFlow.value = devices
        return list
    }

    /**
     * 断开设备连接
     */
    fun disconnect(device: BluetoothDevice) {
        controller.disconnect(device.address)
    }

    /**
     * 断开所有设备连接
     */
    fun disconnectAll() {
        controller.disconnectAll()
    }

    /**
     * 触发断开连接
     * @param address String
     */
    internal fun onDeviceDisconnect(address: String) {
        val list = connectedDevicesFlow.value.toMutableList()
        list.removeAll { it.address == address }
        connectedDevicesFlow.value = list
    }

    /**
     * 打开通知
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param enable Boolean
     */
    fun notify(address: String, serviceUuid: String, characteristicUuid: String, enable: Boolean) {
        controller.notify(address, serviceUuid, characteristicUuid, enable)
    }

    /**
     * 写入数据
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param value ByteArray
     */
    suspend fun write(address: String, serviceUuid: String, characteristicUuid: String, value: ByteArray) {
        value.toList().chunked(splitWriteNum).map { it.toByteArray() }.forEach {
            controller.write(address, serviceUuid, characteristicUuid, it)
        }
    }
}

/**
 * 获取蓝牙实现
 * @return IBluetoothController
 */
expect fun getBluetoothController(): IBluetoothController