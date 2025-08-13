package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.d10ng.app.managers.ActivityManager
import com.d10ng.app.managers.PermissionManager
import com.d10ng.app.status.isLocationEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Android平台蓝牙控制器
 * @Author d10ng
 * @Date 2025/8/12 11:33
 */
object BluetoothControllerAndroid: IBluetoothController {

    private val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val bluetoothScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private const val GATT_MAX_MTU_SIZE = 517
    private const val GATT_MIN_MTU_SIZE = 23
    private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanResults = mutableListOf<ScanResult>()

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Logger.d("[ScanCallback.onScanResult] callbackType: $callbackType, onScanResult: $result")
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
            } else {
                scanResults.add(result)
            }
            BluetoothController.onDeviceScan(BluetoothDevice(result.device.name, result.device.address, result.rssi))
        }

        override fun onScanFailed(errorCode: Int) {
            Logger.e("[ScanCallback.onScanFailed] errorCode: $errorCode")
        }
    }

    private val gattMap = mutableMapOf<String, BluetoothGatt>()

    private val gattEventFlow = MutableSharedFlow<BluetoothGattEvent>(extraBufferCapacity = 1024)

    private val gattCallBack = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt?: return
            Logger.d("[BluetoothGattCallback.onConnectionStateChange] gatt: $gatt, status: $status, newState: $newState")
            gattEventFlow.tryEmit(BluetoothGattOnConnectionStateChangeEvent(gatt, status, newState))
            if (newState == BluetoothProfile.STATE_DISCONNECTED) disconnect(gatt.device.address)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt?: return
            Logger.d("[BluetoothGattCallback.onServicesDiscovered] gatt: $gatt, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnServicesDiscoveredEvent(gatt, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            gatt?: return
            characteristic?: return
            Logger.d("[BluetoothGattCallback.onCharacteristicWrite] gatt: $gatt, characteristic: ${characteristic.uuid}, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnCharacteristicWriteEvent(gatt, characteristic, status))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            gatt?: return
            descriptor?: return
            Logger.d("[BluetoothGattCallback.onDescriptorWrite] gatt: $gatt, descriptor: ${descriptor.uuid}, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnDescriptorWriteEvent(gatt, descriptor, status))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Logger.d("[BluetoothGattCallback.onCharacteristicChanged] gatt: $gatt, characteristic: ${characteristic.uuid}, value: ${value.toHexString(HexFormat.UpperCase)}")
            gattEventFlow.tryEmit(BluetoothGattOnCharacteristicChangedEvent(gatt, characteristic, value))
            val key = "${gatt.device.address} ${characteristic.service.uuid} ${characteristic.uuid}"
            BluetoothController.notifyDataFlow.tryEmit( key to value)
        }

        @Deprecated("Deprecated for Android 13+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            gatt?: return
            characteristic?: return
            Logger.d("[BluetoothGattCallback.onCharacteristicChanged] gatt: $gatt, characteristic: ${characteristic.uuid}, value: ${characteristic.value.toHexString(HexFormat.UpperCase)}")
            gattEventFlow.tryEmit(BluetoothGattOnCharacteristicChangedEvent(gatt, characteristic, characteristic.value))
            val key = "${gatt.device.address} ${characteristic.service.uuid} ${characteristic.uuid}"
            BluetoothController.notifyDataFlow.tryEmit( key to characteristic.value)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            gatt?: return
            Logger.d("[BluetoothGattCallback.onMtuChanged] gatt: $gatt, mtu: $mtu, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnMtuChangedEvent(gatt, mtu, status))
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
        if (isBleSupport().not()) throw Exception("not support ble")
        if (isBleEnable()) return
        // 蓝牙未开启，请求用户开启蓝牙
        ActivityManager.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    @SuppressLint("MissingPermission")
    override fun startScan() {
        scope.launch {
            // 如果Android API小于30，需要请求定位权限
            val isAndroidOver30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.R
            if (isAndroidOver30.not() && PermissionManager.request(locationPermissionArray).not()) throw Exception("missing location permission")
            if (isAndroidOver30.not() && isLocationEnabled().not()) throw Exception("location off")
            if (PermissionManager.request(bluetoothPermissionArray).not()) throw Exception("missing bluetooth permission")
            scanResults.clear()
            bluetoothScanner?.startScan(null, scanSettings, scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        bluetoothScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): List<BluetoothGattService> {
        val device = scanResults.firstOrNull { it.device.address.contentEquals(address, true) }?.device ?: throw Exception("device not found")
        device.connectGatt(ctx, false, gattCallBack)
        val connectRes = gattEventFlow.first {
            it is BluetoothGattOnConnectionStateChangeEvent && it.gatt.device.address.contentEquals(address, true)
        } as BluetoothGattOnConnectionStateChangeEvent
        if (connectRes.status != BluetoothGatt.GATT_SUCCESS) {
            throw Exception("connect failed")
        }
        connectRes.gatt.requestMtu(GATT_MAX_MTU_SIZE)
        gattEventFlow.first {
            it is BluetoothGattOnMtuChangedEvent && it.gatt.device.address.contentEquals(address, true)
        }
        connectRes.gatt.discoverServices()
        val discoveredEvent = gattEventFlow.first {
            it is BluetoothGattOnServicesDiscoveredEvent && it.gatt.device.address.contentEquals(address, true)
        } as BluetoothGattOnServicesDiscoveredEvent
        if (discoveredEvent.status != BluetoothGatt.GATT_SUCCESS) {
            discoveredEvent.gatt.close()
            throw Exception("discover services failed")
        }
        discoveredEvent.gatt.let { gatt ->
            val list = mutableListOf<BluetoothGattService>()
            gatt.services.forEach { serviceUuid ->
                gatt.getService(serviceUuid.uuid)?.let { service ->
                    val serviceItem = BluetoothGattService(service.uuid.toString().uppercase(), service.characteristics.map { characteristic ->
                        BluetoothGattCharacteristic(characteristic.uuid.toString().uppercase(), characteristic.properties)
                    })
                    list.add(serviceItem)
                }
            }
            gattMap[address] = gatt
            return list
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect(address: String) {
        gattMap[address]?.close()
        gattMap.remove(address)
        BluetoothController.onDeviceDisconnect(address)
    }

    override fun disconnectAll() {
        gattMap.keys.forEach { disconnect(it) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        val gatt = gattMap[address] ?: throw Exception("device not connected")
        gatt.findCharacteristic(UUID.fromString(characteristicUuid), UUID.fromString(serviceUuid))?.let { characteristic ->
            val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
            if (descriptor == null) throw Exception("getDescriptor failed")
            if (gatt.setCharacteristicNotification(characteristic, enable).not()) throw Exception("setCharacteristicNotification failed")
            descriptor.executeWrite(gatt, if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            val notifyRes = gattEventFlow.first {
                it is BluetoothGattOnDescriptorWriteEvent
                        && it.gatt.device.address.contentEquals(address, true)
                        && it.descriptor.uuid == descriptor.uuid
            } as BluetoothGattOnDescriptorWriteEvent
            if (notifyRes.status != BluetoothGatt.GATT_SUCCESS) throw Exception("notify failed")
        }?: throw Exception("findCharacteristic failed")
    }

    override suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        val gatt = gattMap[address] ?: throw Exception("device not connected")
        gatt.findCharacteristic(UUID.fromString(characteristicUuid), UUID.fromString(serviceUuid))?.let { characteristic ->
            val writeType = when {
                characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else -> throw Exception("Characteristic ${characteristic.uuid} cannot be written to")
            }
            characteristic.executeWrite(gatt, value, writeType)
            val writeRes = gattEventFlow.first {
                it is BluetoothGattOnCharacteristicWriteEvent
                        && it.gatt.device.address.contentEquals(address, true)
                        && it.characteristic.uuid.toString().contentEquals(characteristicUuid, true)
            } as BluetoothGattOnCharacteristicWriteEvent
            if (writeRes.status != BluetoothGatt.GATT_SUCCESS) {
                throw Exception("write failed, status: ${writeRes.status}")
            }
        }?: throw Exception("Characteristic not found")
    }
}

internal interface BluetoothGattEvent {}
internal data class BluetoothGattOnConnectionStateChangeEvent(val gatt: BluetoothGatt, val status: Int, val newState: Int): BluetoothGattEvent
internal data class BluetoothGattOnServicesDiscoveredEvent(val gatt: BluetoothGatt, val status: Int): BluetoothGattEvent
internal data class BluetoothGattOnCharacteristicWriteEvent(val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic, val status: Int): BluetoothGattEvent
internal data class BluetoothGattOnDescriptorWriteEvent(val gatt: BluetoothGatt, val descriptor: BluetoothGattDescriptor, val status: Int): BluetoothGattEvent
internal class BluetoothGattOnCharacteristicChangedEvent(val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic, val value: ByteArray): BluetoothGattEvent
internal data class BluetoothGattOnMtuChangedEvent(val gatt: BluetoothGatt, val mtu: Int, val status: Int): BluetoothGattEvent

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
    properties and property != 0

fun BluetoothGatt.findCharacteristic(
    characteristicUuid: UUID,
    serviceUuid: UUID? = null
): BluetoothGattCharacteristic? {
    return if (serviceUuid != null) {
        // If serviceUuid is available, use it to disambiguate cases where multiple services have
        // distinct characteristics that happen to use the same UUID
        services
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics?.firstOrNull { it.uuid == characteristicUuid }
    } else {
        // Iterate through services and find the first one with a match for the characteristic UUID
        services?.forEach { service ->
            service.characteristics?.firstOrNull { characteristic ->
                characteristic.uuid == characteristicUuid
            }?.let { matchingCharacteristic ->
                return matchingCharacteristic
            }
        }
        return null
    }
}

@SuppressLint("MissingPermission")
fun BluetoothGattCharacteristic.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(this, payload, writeType)
    } else {
        // Fall back to deprecated version of writeCharacteristic for Android <13
        legacyCharacteristicWrite(gatt, payload, writeType)
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.legacyCharacteristicWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
) {
    this.writeType = writeType
    value = payload
    gatt.writeCharacteristic(this)
}

@SuppressLint("MissingPermission")
fun BluetoothGattDescriptor.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(this, payload)
    } else {
        // Fall back to deprecated version of writeDescriptor for Android <13
        legacyDescriptorWrite(gatt, payload)
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.legacyDescriptorWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
) {
    value = payload
    gatt.writeDescriptor(this)
}