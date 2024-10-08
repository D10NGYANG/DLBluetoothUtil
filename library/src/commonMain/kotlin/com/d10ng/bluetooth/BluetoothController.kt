package com.d10ng.bluetooth

import com.d10ng.common.base.toHexString
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
        set(value) {
            Logger.i("set splitWriteNum = $value")
            field = value
        }

    /**
     * 设置日志输出
     * @param debug Boolean
     */
    fun setDebug(debug: Boolean) {
        Logger.debug = debug
    }

    /**
     * 是否支持蓝牙
     * @return Boolean
     */
    fun isBleSupport(): Boolean = controller.isBleSupport()

    /**
     * 是否已开启蓝牙
     * @return Boolean
     */
    fun isBleEnable(): Boolean = controller.isBleEnable()

    /**
     * 开启蓝牙
     */
    suspend fun bleEnable() = controller.bleEnable()

    /**
     * 开始扫描
     */
    fun startScan() {
        scanDevicesFlow.value = listOf()
        cancelScan()
        scanningFlow.value = true
        Logger.i("开始蓝牙扫描")
        controller.startScan()
    }

    /**
     * 取消扫描
     */
    fun cancelScan() {
        scanningFlow.value = false
        Logger.i("取消蓝牙扫描")
        controller.stopScan()
    }

    /**
     * 设备扫描
     * @param device BluetoothDevice
     */
    internal fun onDeviceScan(device: BluetoothDevice) {
        if (device.name == null) return
        Logger.i("扫描到设备：${device.name}(${device.address})，信号强度：${device.rssi}")
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
        Logger.i("连接设备：${device.name}(${device.address})")
        val list = controller.connect(device.address)
        Logger.i("设备连接成功，得到设备服务特征信息：${list}")
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
        Logger.i("断开设备：${device.name}(${device.address})")
        controller.disconnect(device.address)
    }

    /**
     * 断开所有设备连接
     */
    fun disconnectAll() {
        Logger.i("断开所有设备")
        controller.disconnectAll()
    }

    /**
     * 触发断开连接
     * @param address String
     */
    internal fun onDeviceDisconnect(address: String) {
        Logger.i("设备断开连接：${address}")
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
        Logger.i("打开通知：${address}，$serviceUuid，$characteristicUuid，$enable")
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
        value.toList().chunked(splitWriteNum).map { it.toByteArray() }.forEach { data ->
            Logger.i("写入数据：${address}，$serviceUuid，$characteristicUuid，${data.toHexString()}")
            controller.write(address, serviceUuid, characteristicUuid, data)
        }
    }
}

/**
 * 获取蓝牙实现
 * @return IBluetoothController
 */
expect fun getBluetoothController(): IBluetoothController