package com.d10ng.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.d10ng.app.managers.ActivityManager
import com.d10ng.app.managers.PermissionManager
import com.d10ng.app.status.isLocationEnabled
import com.d10ng.common.base.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 蓝牙控制器-第三方库
 * @Author d10ng
 * @Date 2024/11/12 11:21
 */
object BluetoothControllerAndroid3: IBluetoothController {

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    private val connectEvent = MutableSharedFlow<BleGattCallbackEvent>()
    private val connectedDevices = mutableListOf<BleDevice>()

    private val connectCallback = object : BleGattCallback() {
        override fun onStartConnect() {
            Logger.i("开始连接设备")
            scope.launch { connectEvent.emit(BleGattCallbackOnStartConnect()) }
        }

        override fun onConnectFail(p0: BleDevice?, p1: BleException?) {
            val device = p0?: return
            Logger.i("设备连接失败 ${device.name} ${device.mac} ${p1?.description}")
            scope.launch { connectEvent.emit(BleGattCallbackOnConnectFail(p0, p1)) }
        }

        override fun onConnectSuccess(p0: BleDevice?, p1: BluetoothGatt?, p2: Int) {
            val device = p0?: return
            Logger.i("设备连接成功 ${device.name} ${device.mac}")
            connectedDevices.add(device)
            scope.launch { connectEvent.emit(BleGattCallbackOnConnectSuccess(p0, p1, p2)) }
        }

        override fun onDisConnected(p0: Boolean, p1: BleDevice?, p2: BluetoothGatt?, p3: Int) {
            val device = p1?: return
            Logger.i("设备断开连接 ${device.name} ${device.mac} ${if (p0) "主动断开" else "被动断开"}")
            connectedDevices.removeAll { it.mac.contentEquals(device.mac, true) }
            BluetoothController.onDeviceDisconnect(device.device.address)
            scope.launch { connectEvent.emit(BleGattCallbackOnDisConnected(p0, p1, p2, p3)) }
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
        scope.launch {
            // 如果Android API小于30，需要请求定位权限
            val isAndroidOver30 = android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.R
            if (isAndroidOver30.not() && PermissionManager.request(locationPermissionArray).not()) throw LocationPermissionException()
            if (isAndroidOver30.not() && isLocationEnabled().not()) throw LocationOffException()
            if (PermissionManager.request(bluetoothPermissionArray).not()) throw BluetoothPermissionException()
            withContext(Dispatchers.Main) {
                BleManager.getInstance().scan(object : BleScanCallback() {
                    override fun onScanStarted(p0: Boolean) {
                        Logger.i("开始扫描 $p0")
                    }

                    override fun onScanning(p0: BleDevice?) {
                        p0?: return
                        BluetoothController.onDeviceScan(BluetoothDevice(p0.name, p0.device.address!!, p0.rssi))
                    }

                    override fun onScanFinished(p0: MutableList<BleDevice>?) {
                        Logger.i("扫描完成")
                    }
                })
            }
        }
    }

    override fun stopScan() {
        runCatching { BleManager.getInstance().cancelScan() }
    }

    override suspend fun connect(address: String): List<BluetoothGattService> {
        BleManager.getInstance().connect(address, connectCallback)
        val connectRes = connectEvent.first { it is BleGattCallbackOnConnectFail || it is BleGattCallbackOnConnectSuccess }
        if (connectRes is BleGattCallbackOnConnectFail) {
            throw Exception("连接失败！${connectRes.exception?: ""}")
        } else {
            connectRes as BleGattCallbackOnConnectSuccess
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
        }
    }

    override fun disconnect(address: String) {
        connectedDevices.filter { it.mac.contentEquals(address, true) }.forEach {
            BleManager.getInstance().disconnect(it)
        }
    }

    override fun disconnectAll() {
        BleManager.getInstance().disconnectAllDevice()
    }

    override suspend fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        val device = connectedDevices.first { it.mac.contentEquals(address, true) }
        if (enable) {
            val key = "$address $serviceUuid $characteristicUuid"
            BleManager.getInstance().notify(device, serviceUuid, characteristicUuid, BleNotifyCallbackManager.getCallback(key))
            val notifyRes = BleNotifyCallbackManager.eventFlow.first { it is BleNotifyCallbackEventOnNotifySuccess || it is BleNotifyCallbackEventOnNotifyFailure }
            if (notifyRes is BleNotifyCallbackEventOnNotifyFailure) {
                throw Exception("打开${notifyRes.key}通知失败！${notifyRes.exception?: ""}")
            }
        } else {
            BleManager.getInstance().stopNotify(device, serviceUuid, characteristicUuid)
        }
    }

    override suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        val device = connectedDevices.first { it.mac.contentEquals(address, true) }
        val res = suspendCancellableCoroutine { cont ->
            BleManager.getInstance().write(device, serviceUuid, characteristicUuid, value,
                object : BleWriteCallback() {
                    override fun onWriteSuccess(p0: Int, p1: Int, p2: ByteArray?) {
                        Logger.i("发送成功，${p2?.toHexString(false, uppercase = true)}")
                        if (p0 < p1) return
                        // 全部发完
                        Logger.i("发送完成，共${p1}包，${value.toHexString()}")
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onWriteFailure(p0: BleException?) {
                        Logger.i("发送失败，${p0?.description}")
                        if (cont.isActive) cont.resume(false)
                    }
                })
        }
        if (!res) throw Exception("写入失败！")
    }
}

internal interface BleGattCallbackEvent
internal class BleGattCallbackOnStartConnect(): BleGattCallbackEvent
internal data class BleGattCallbackOnConnectFail(val bleDevice: BleDevice, val exception: BleException?): BleGattCallbackEvent
internal data class BleGattCallbackOnConnectSuccess(val bleDevice: BleDevice, val gatt: BluetoothGatt?, val status: Int): BleGattCallbackEvent
internal data class BleGattCallbackOnDisConnected(val isActiveDisConnected: Boolean, val bleDevice: BleDevice, val gatt: BluetoothGatt?, val status: Int): BleGattCallbackEvent

internal interface BleNotifyCallbackDataEvent
internal data class BleNotifyCallbackEventOnNotifySuccess(val key: String): BleNotifyCallbackDataEvent
internal data class BleNotifyCallbackEventOnNotifyFailure(val key: String, val exception: BleException?): BleNotifyCallbackDataEvent
internal class BleNotifyCallbackEventOnCharacteristicChanged(val key: String, val data: ByteArray): BleNotifyCallbackDataEvent

internal object BleNotifyCallbackManager {

    private val map = mutableMapOf<String, BleNotifyCallbackData>()
    val eventFlow = MutableSharedFlow<BleNotifyCallbackDataEvent>(extraBufferCapacity = 1024)

    fun getCallback(key: String): BleNotifyCallback {
        val callback = map[key]?: BleNotifyCallbackData(key)
        map[key] = callback
        return callback.callback
    }

    fun emitEvent(event: BleNotifyCallbackDataEvent) {
        eventFlow.tryEmit(event)
    }
}

internal class BleNotifyCallbackData(
    key: String
) {
    val callback = object : BleNotifyCallback() {
        override fun onNotifySuccess() {
            Logger.i("打开通知成功，$key")
            BleNotifyCallbackManager.emitEvent(BleNotifyCallbackEventOnNotifySuccess(key))
        }

        override fun onNotifyFailure(p0: BleException?) {
            Logger.i("打开通知失败，$key，${p0?.description}")
            BleNotifyCallbackManager.emitEvent(BleNotifyCallbackEventOnNotifyFailure(key, p0))
        }

        override fun onCharacteristicChanged(p0: ByteArray?) {
            p0?: return
            BleNotifyCallbackManager.emitEvent(BleNotifyCallbackEventOnCharacteristicChanged(key, p0))
            BluetoothController.notifyDataFlow.tryEmit( key to p0)
        }
    }
}