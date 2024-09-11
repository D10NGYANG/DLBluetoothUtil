package com.d10ng.bluetooth

import com.d10ng.common.transform.decodeGBK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 蓝牙控制器
 * @Author d10ng
 * @Date 2024/9/10 15:14
 */
object BluetoothController {

    private val scope = CoroutineScope(Dispatchers.Default)
    // 已连接设备列表
    val connectedDevicesFlow = MutableStateFlow(listOf<BluetoothDevice>())
    // 扫描设备列表
    val scanDevicesFlow = MutableStateFlow(listOf<BluetoothDevice>())
    // 是否正在扫描
    val scanningFlow = MutableStateFlow(false)
    // 通知数据
    val notifyDataFlow = MutableSharedFlow<Pair<String, ByteArray>>()

    init {
        scope.launch {
            notifyDataFlow.collect {
                println("收到 ${it.first} 通知数据: ${it.second.decodeGBK()}")
            }
        }
    }

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
        val list = BluetoothControllerMultiplatform.connect(device.address)
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
        BluetoothControllerMultiplatform.disconnect(device.address)
    }

    /**
     * 断开所有设备连接
     */
    fun disconnectAll() {
        BluetoothControllerMultiplatform.disconnectAll()
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
        BluetoothControllerMultiplatform.notify(address, serviceUuid, characteristicUuid, enable)
    }

    /**
     * 写入数据
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param value ByteArray
     */
    suspend fun write(address: String, serviceUuid: String, characteristicUuid: String, value: ByteArray) {
        BluetoothControllerMultiplatform.write(address, serviceUuid, characteristicUuid, value)
    }
}