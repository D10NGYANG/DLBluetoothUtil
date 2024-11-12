package com.d10ng.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.bhm.ble.BleManager
import com.bhm.ble.callback.BleConnectCallback
import com.bhm.ble.callback.BleNotifyCallback
import com.bhm.ble.data.BleConnectFailType
import com.bhm.ble.data.BleDescriptorGetType
import com.bhm.ble.device.BleDevice
import com.d10ng.app.managers.ActivityManager
import com.d10ng.app.managers.PermissionManager
import com.d10ng.app.status.isLocationEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 蓝牙控制器-第三方库
 * @Author d10ng
 * @Date 2024/10/29 15:21
 */
object BluetoothControllerAndroid2: IBluetoothController {

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val scope by lazy { CoroutineScope(Dispatchers.IO) }

    private val connectEventFlow = MutableSharedFlow<BleConnectCallbackEvent>()
    private val connectedDevices = mutableListOf<BleDevice>()
    private val notifyCallbackEventFlow = MutableSharedFlow<BleNotifyCallbackEvent>()
    private val notifyKeys = mutableMapOf<String, String>()

    private val connectCallback: BleConnectCallback.() -> Unit = {
        onConnectStart {
            scope.launch { connectEventFlow.emit(BleConnectCallbackOnConnectStart()) }
        }
        onConnectFail { bleDevice, connectFailType ->
            scope.launch { connectEventFlow.emit(BleConnectCallbackOnConnectFail(bleDevice, connectFailType)) }
        }
        onConnectSuccess { bleDevice, gatt ->
            connectedDevices.add(bleDevice)
            scope.launch { connectEventFlow.emit(BleConnectCallbackOnConnectSuccess(bleDevice, gatt)) }
        }
        onDisConnected { isActiveDisConnected, bleDevice, gatt, status ->
            connectedDevices.remove(bleDevice)
            BluetoothController.onDeviceDisconnect(bleDevice.deviceAddress!!)
            scope.launch { connectEventFlow.emit(BleConnectCallbackOnDisConnected(isActiveDisConnected, bleDevice, gatt, status)) }
        }
    }

    private val notifyCallback: BleNotifyCallback.() -> Unit = {
        onNotifyFail { bleDevice, notifyUUID, throwable ->
            println("onNotifyFail: $bleDevice, $notifyUUID, $throwable")
            scope.launch { notifyCallbackEventFlow.emit(BleNotifyCallbackOnNotifyFail(bleDevice, notifyUUID, throwable)) }
        }
        onNotifySuccess { bleDevice, notifyUUID ->
            println("onNotifySuccess: $bleDevice, $notifyUUID")
            scope.launch { notifyCallbackEventFlow.emit(BleNotifyCallbackOnNotifySuccess(bleDevice, notifyUUID)) }
        }
        onCharacteristicChanged { bleDevice, notifyUUID, data ->
            scope.launch {
                notifyCallbackEventFlow.emit(BleNotifyCallbackOnCharacteristicChanged(bleDevice, notifyUUID, data))
                notifyKeys["${bleDevice.deviceAddress} $notifyUUID"]?.let { BluetoothController.notifyDataFlow.emit( it to data) }
            }
        }
    }

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
        if (isBleSupport().not()) throw BluetoothNotSupportException()
        if (isBleEnable()) return
        // 蓝牙未开启，请求用户开启蓝牙
        ActivityManager.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun startScan() {
        stopScan()
        scope.launch(Dispatchers.Main) {
            // 如果Android API小于30，需要请求定位权限
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R && PermissionManager.request(locationPermissionArray).not()) throw LocationPermissionException()
            if (PermissionManager.request(bluetoothPermissionArray).not()) throw BluetoothPermissionException()
            if (isLocationEnabled().not()) throw LocationOffException()
            BleManager.get().startScan {
                onLeScan { bleDevice, _ ->
                    bleDevice.deviceAddress?:return@onLeScan
                    runCatching {
                        BluetoothController.onDeviceScan(BluetoothDevice(bleDevice.deviceName, bleDevice.deviceAddress!!, bleDevice.rssi!!))
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
            }
        }
    }

    override fun stopScan() {
        BleManager.get().stopScan()
    }

    override suspend fun connect(address: String): List<BluetoothGattService> {
        withContext(Dispatchers.Main) { BleManager.get().connect(address, false, connectCallback) }
        val connectRes = connectEventFlow.first { it is BleConnectCallbackOnConnectSuccess || it is BleConnectCallbackOnConnectFail }
        if (connectRes is BleConnectCallbackOnConnectSuccess) {
            //BleManager.get().setConnectionPriority(connectRes.bleDevice, BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            connectRes.gatt?.let { gatt ->
                val list = mutableListOf<BluetoothGattService>()
                gatt.services.forEach { serviceUuid ->
                    gatt.getService(serviceUuid.uuid)?.let { service ->
                        val serviceItem = BluetoothGattService(service.uuid.toString().uppercase(), service.characteristics.map { characteristic ->
                            BluetoothGattCharacteristic(characteristic.uuid.toString().uppercase(), characteristic.properties)
                        })
                        list.add(serviceItem)
                    }
                }
                return list
            }
            return emptyList()
        } else {
            throw Exception("连接失败，错误信息:${(connectRes as BleConnectCallbackOnConnectFail).connectFailType}")
        }
    }

    override fun disconnect(address: String) {
        BleManager.get().disConnect(address)
    }

    override fun disconnectAll() {
        BleManager.get().disConnectAll()
    }

    override suspend fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        val device = connectedDevices.firstOrNull { it.deviceAddress.contentEquals(address) }?: return
        if (enable) {
            BleManager.get().notify(device, serviceUuid, characteristicUuid, BleDescriptorGetType.Default, notifyCallback)
            val notifyRes = notifyCallbackEventFlow.first { it is BleNotifyCallbackOnNotifySuccess || it is BleNotifyCallbackOnNotifyFail }
            if (notifyRes is BleNotifyCallbackOnNotifyFail) {
                throw Exception("通知失败，错误信息:${notifyRes.throwable}")
            }
            // TODO 暂时不允许失败
            notifyKeys["$address $characteristicUuid"] = "$address $serviceUuid $characteristicUuid"
        } else {
            BleManager.get().stopNotify(device, serviceUuid, characteristicUuid, BleDescriptorGetType.Default)
        }
    }

    override suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) = suspendCoroutine { cont ->
        val device = connectedDevices.firstOrNull { it.deviceAddress.contentEquals(address) }
        if (device == null) {
            cont.resumeWithException(Exception("设备未连接"))
            return@suspendCoroutine
        }
        BleManager.get().writeData(device, serviceUuid, characteristicUuid, value) {
            onWriteComplete { _, allSuccess ->
                if (allSuccess) cont.resume(Unit)
                else cont.resumeWithException(Exception("写入失败"))
            }
        }
    }
}

internal interface BleConnectCallbackEvent {}
internal class BleConnectCallbackOnConnectStart(): BleConnectCallbackEvent
internal data class BleConnectCallbackOnConnectFail(val bleDevice: BleDevice, val connectFailType: BleConnectFailType): BleConnectCallbackEvent
internal data class BleConnectCallbackOnConnectSuccess(val bleDevice: BleDevice, val gatt: BluetoothGatt?): BleConnectCallbackEvent
internal data class BleConnectCallbackOnDisConnected(val isActiveDisConnected: Boolean, val bleDevice: BleDevice, val gatt: BluetoothGatt?, val status: Int): BleConnectCallbackEvent

internal interface BleNotifyCallbackEvent {}
internal data class BleNotifyCallbackOnNotifySuccess(val bleDevice: BleDevice, val notifyUUID: String): BleNotifyCallbackEvent
internal data class BleNotifyCallbackOnNotifyFail(val bleDevice: BleDevice, val notifyUUID: String, val throwable: Throwable): BleNotifyCallbackEvent
internal data class BleNotifyCallbackOnCharacteristicChanged(val bleDevice: BleDevice, val notifyUUID: String, val data: ByteArray): BleNotifyCallbackEvent
