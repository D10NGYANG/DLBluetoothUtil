package com.d10ng.bluetooth

import android.annotation.SuppressLint
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.GattClientScope
import androidx.bluetooth.ScanResult
import com.d10ng.app.managers.PermissionManager
import com.d10ng.app.status.isLocationEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
actual object BluetoothControllerMultiplatform {

    private val ble by lazy { BluetoothLe(ctx) }
    private val scope by lazy { CoroutineScope(Dispatchers.IO) }
    private var scanJob: Job? = null
    private val scanResults = mutableListOf<ScanResult>()
    private val connections = mutableMapOf<String, GattClientScope>()

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
    actual suspend fun connect(device: BluetoothDevice): List<BluetoothGattService> {
        val item = scanResults.find { it.deviceAddress.address.contentEquals(device.address) } ?: throw DeviceNotFoundException()
        val scope = ble.connectGatt(item.device) { this }
        val result = mutableListOf<BluetoothGattService>()
        scope.services.forEach { service ->
            val serviceItem = BluetoothGattService(service.uuid.toString(), service.characteristics.map { characteristic ->
                BluetoothGattCharacteristic(characteristic.uuid.toString(), characteristic.properties)
            })
            result.add(serviceItem)
        }
        connections[device.address] = scope
        return result
    }

    actual fun disconnect(device: BluetoothDevice) {
        connections.remove(device.address)
    }

    actual fun disconnectAll() {
        connections.clear()
    }

}