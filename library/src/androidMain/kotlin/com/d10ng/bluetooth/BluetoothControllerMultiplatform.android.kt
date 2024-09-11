package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.GattClientScope
import androidx.bluetooth.ScanResult
import com.d10ng.app.managers.ActivityManager
import com.d10ng.app.managers.PermissionManager
import com.d10ng.app.status.isLocationEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
actual object BluetoothControllerMultiplatform {

    private val ble by lazy { BluetoothLe(ctx) }
    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val scope by lazy { CoroutineScope(Dispatchers.IO) }
    private var scanJob: Job? = null
    private val scanResults = mutableListOf<ScanResult>()
    private val connectJobMap: MutableMap<String, Job> = mutableMapOf()
    private val connections = mutableMapOf<String, GattClientScope>()
    private val subscribeJobMap = mutableMapOf<String, Job>()

    actual fun isBleSupport(): Boolean {
        // 检查设备是否支持蓝牙
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return false
        }
        // 获取 BluetoothAdapter
        bluetoothAdapter ?: return false
        // 检查设备是否支持蓝牙 BLE
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    actual fun isBleEnable(): Boolean {
        if (isBleSupport().not()) return false
        // 检查蓝牙是否已开启
        return bluetoothAdapter!!.isEnabled
    }

    actual suspend fun bleEnable() {
        if (isBleSupport().not()) throw BluetoothNotSupportException()
        if (isBleEnable()) return
        // 蓝牙未开启，请求用户开启蓝牙
        ActivityManager.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    @SuppressLint("MissingPermission")
    actual fun startScan() {
        stopScan()
        scanJob = scope.launch {
            if (PermissionManager.request(locationPermissionArray).not()) throw LocationPermissionException()
            if (PermissionManager.request(bluetoothPermissionArray).not()) throw BluetoothPermissionException()
            if (isLocationEnabled().not()) throw LocationOffException()
            ble.scan().collect {
                scanResults.removeAll { item -> item.deviceAddress.address.contentEquals(it.deviceAddress.address) }
                scanResults.add(it)
                BluetoothController.onDeviceScan(BluetoothDevice(it.device.name, it.deviceAddress.address, it.rssi))
            }
        }
    }

    actual fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    @SuppressLint("MissingPermission")
    actual suspend fun connect(address: String): List<BluetoothGattService> {
        val item = scanResults.find { it.deviceAddress.address.contentEquals(address) } ?: throw DeviceNotFoundException()
        connectJobMap[address] = scope.launch {
            ble.connectGatt(item.device) {
                connections[address] = this
                awaitCancellation()
            }
        }.apply {
            invokeOnCompletion {
                println("断开连接，${address}，${it?.message}")
                connectJobMap.remove(address)
                connections.remove(address)
                val maps = subscribeJobMap.filterKeys { key -> key.split(" ")[0].contentEquals(address) }
                maps.forEach { map -> map.value.cancel(); subscribeJobMap.remove(map.key) }
                BluetoothController.onDeviceDisconnect(address)
            }
        }
        while (connections[address] == null) {
            // 等待完成连接
            delay(10)
        }
        val clientScope = connections[address]!!
        val result = mutableListOf<BluetoothGattService>()
        clientScope.services.forEach { service ->
            val serviceItem = BluetoothGattService(service.uuid.toString(), service.characteristics.map { characteristic ->
                BluetoothGattCharacteristic(characteristic.uuid.toString(), characteristic.properties)
            })
            result.add(serviceItem)
        }
        return result
    }

    actual fun disconnect(address: String) {
        connectJobMap[address]?.cancel()
        connectJobMap.remove(address)
        connections.remove(address)
    }

    actual fun disconnectAll() {
        connectJobMap.values.forEach { it.cancel() }
        connectJobMap.clear()
        connections.clear()
    }

    /**
     * 打开或关闭通知
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param enable Boolean
     */
    actual fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        val key = "$address $serviceUuid $characteristicUuid"
        if (enable.not()) {
            subscribeJobMap[key]?.cancel()
            subscribeJobMap.remove(key)
            return
        }
        val findSubscribeFlow = subscribeJobMap[key]
        if (findSubscribeFlow != null) return
        val clientScope = connections[address] ?: throw DeviceNotConnectedException()
        val server = clientScope.getService(UUID.fromString(serviceUuid))?: throw Exception("找不到服务")
        val characteristic = server.getCharacteristic(UUID.fromString(characteristicUuid))?: throw Exception("找不到特征")
        if (characteristic.properties.bleGattCharacteristicNotifiable().not()) throw Exception("不支持通知")
        subscribeJobMap[key] = scope.launch {
            val curKey = key
            clientScope.subscribeToCharacteristic(characteristic).collect {
                BluetoothController.notifyDataFlow.emit(curKey to it)
            }
        }
    }

    /**
     * 写入数据
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param value ByteArray
     */
    actual suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        val clientScope = connections[address] ?: throw DeviceNotConnectedException()
        val server = clientScope.getService(UUID.fromString(serviceUuid))?: throw Exception("找不到服务")
        val characteristic = server.getCharacteristic(UUID.fromString(characteristicUuid))?: throw Exception("找不到特征")
        val result = clientScope.writeCharacteristic(characteristic, value)
        if (result.isFailure) throw Exception("写入失败")
    }
}