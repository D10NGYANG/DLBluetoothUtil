package com.d10ng.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.d10ng.app.managers.ActivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android平台蓝牙控制器
 * @Author d10ng
 * @Date 2025/8/12 11:33
 */
object BluetoothControllerAndroid: IBluetoothController {

    private val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    override fun isBleSupport(): Boolean {
        // 检查设备是否支持蓝牙
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return false
        }
        // 获取 BluetoothAdapter
        bluetoothAdapter ?: return false
        // 检查设备是否支持蓝牙 BLE
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    override fun isBleEnable(): Boolean {
        if (isBleSupport().not()) return false
        // 检查蓝牙是否已开启
        return bluetoothAdapter!!.isEnabled
    }

    override suspend fun bleEnable() {
        if (isBleSupport().not()) throw Exception("not support ble")
        if (isBleEnable()) return
        // 蓝牙未开启，请求用户开启蓝牙
        ActivityManager.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun startScan() {
        TODO("Not yet implemented")
    }

    override fun stopScan() {
        TODO("Not yet implemented")
    }

    override suspend fun connect(address: String): List<BluetoothGattService> {
        TODO("Not yet implemented")
    }

    override fun disconnect(address: String) {
        TODO("Not yet implemented")
    }

    override fun disconnectAll() {
        TODO("Not yet implemented")
    }

    override suspend fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        TODO("Not yet implemented")
    }
}