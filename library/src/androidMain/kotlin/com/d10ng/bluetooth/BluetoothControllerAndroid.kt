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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Android平台蓝牙控制器
 * @Author d10ng
 * @Date 2025/8/12 11:33
 */
@SuppressLint("MissingPermission")
object BluetoothControllerAndroid : IBluetoothController {

    private val scope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    // 操作任务队列
    private val operationQueueChannel = Channel<OperationType>(capacity = Channel.UNLIMITED)

    // 操作结果
    private val operationResultFlow = MutableSharedFlow<OperationResult>(extraBufferCapacity = Int.MAX_VALUE)

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

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Logger.d("[ScanCallback.onScanResult] callbackType: $callbackType, result: $result")
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

    private val gattEventFlow = MutableSharedFlow<BluetoothGattEvent>(extraBufferCapacity = Int.MAX_VALUE)

    private val gattCallBack = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt ?: return
            Logger.d("[BluetoothGattCallback.onConnectionStateChange] device: ${gatt.device.name}, status: $status, newState: $newState")
            gattEventFlow.tryEmit(BluetoothGattOnConnectionStateChangeEvent(gatt, status, newState))
            if (newState == BluetoothProfile.STATE_DISCONNECTED) disconnect(gatt.device.address)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt ?: return
            Logger.d("[BluetoothGattCallback.onServicesDiscovered] device: ${gatt.device.name}, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnServicesDiscoveredEvent(gatt, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            gatt ?: return
            characteristic ?: return
            Logger.d("[BluetoothGattCallback.onCharacteristicWrite] device: ${gatt.device.name}, characteristic: ${characteristic.uuid}, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnCharacteristicWriteEvent(gatt, characteristic, status))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            gatt ?: return
            descriptor ?: return
            Logger.d("[BluetoothGattCallback.onDescriptorWrite] device: ${gatt.device.name}, descriptor: ${descriptor.uuid}, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnDescriptorWriteEvent(gatt, descriptor, status))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Logger.d("[BluetoothGattCallback.onCharacteristicChanged] device: ${gatt.device.name}, characteristic: ${characteristic.uuid}, value: ${value.toHexString(HexFormat.UpperCase)}")
            gattEventFlow.tryEmit(BluetoothGattOnCharacteristicChangedEvent(gatt, characteristic, value))
            val key = "${gatt.device.address} ${characteristic.service.uuid} ${characteristic.uuid}"
            BluetoothController.notifyDataFlow.tryEmit(key to value)
        }

        @Deprecated("Deprecated for Android 13+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            gatt ?: return
            characteristic ?: return
            Logger.d("[BluetoothGattCallback.onCharacteristicChanged] device: ${gatt.device.name}, characteristic: ${characteristic.uuid}, value: ${characteristic.value.toHexString(HexFormat.UpperCase)}")
            gattEventFlow.tryEmit(BluetoothGattOnCharacteristicChangedEvent(gatt, characteristic, characteristic.value))
            val key = "${gatt.device.address} ${characteristic.service.uuid} ${characteristic.uuid}"
            BluetoothController.notifyDataFlow.tryEmit(key to characteristic.value)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            gatt ?: return
            Logger.d("[BluetoothGattCallback.onMtuChanged] device: ${gatt.device.name}, mtu: $mtu, status: $status")
            gattEventFlow.tryEmit(BluetoothGattOnMtuChangedEvent(gatt, mtu, status))
        }
    }

    init {
        scope.launch {
            // 监听操作队列，串列执行
            for (operation in operationQueueChannel) {
                when (operation) {
                    is OperationTypeConnect -> {
                        // 连接设备
                        val device = scanResults.firstOrNull {
                            it.device.address.contentEquals(operation.address, true)
                        }?.device
                        if (device == null) {
                            Logger.w("[OperationTypeConnect] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        device.connectGatt(ctx, false, gattCallBack)
                        val connectEvent = awaitFirstEvent<BluetoothGattOnConnectionStateChangeEvent>(operation.address, 5000L)
                        if (connectEvent == null || connectEvent.status != BluetoothGatt.GATT_SUCCESS) {
                            Logger.w("[OperationTypeConnect] fail 连接失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        Logger.d("[OperationTypeConnect] success 连接成功")
                        gattMap[operation.address] = connectEvent.gatt
                        operationResultFlow.tryEmit(operation.success())
                    }

                    is OperationTypeDiscoverServices -> {
                        // 获取服务
                        val gatt = gattMap[operation.address]
                        if (gatt == null) {
                            Logger.w("[OperationTypeDiscoverServices] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (!gatt.discoverServices()) {
                            Logger.w("[OperationTypeDiscoverServices] fail 获取服务失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val discoverServicesEvent = awaitFirstEvent<BluetoothGattOnServicesDiscoveredEvent>(operation.address, 3000L)
                        if (discoverServicesEvent == null || discoverServicesEvent.status != BluetoothGatt.GATT_SUCCESS) {
                            Logger.w("[OperationTypeDiscoverServices] fail 获取服务失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val list = mutableListOf<BluetoothGattService>()
                        discoverServicesEvent.gatt.let { gatt ->
                            gatt.services.forEach { serviceUuid ->
                                gatt.getService(serviceUuid.uuid)?.let { service ->
                                    val serviceItem = BluetoothGattService(
                                        service.uuid.toString().uppercase(),
                                        service.characteristics.map { characteristic ->
                                            BluetoothGattCharacteristic(
                                                characteristic.uuid.toString().uppercase(),
                                                characteristic.properties
                                            )
                                        }
                                    )
                                    list.add(serviceItem)
                                }
                            }
                        }
                        Logger.d("[OperationTypeDiscoverServices] success 获取服务成功")
                        gattMap[operation.address] = discoverServicesEvent.gatt
                        operationResultFlow.tryEmit(operation.success(list))
                    }

                    is OperationTypeMtuChanged -> {
                        // 获取 MTU
                        val gatt = gattMap[operation.address]
                        if (gatt == null) {
                            Logger.w("[OperationTypeMtuChanged] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (!gatt.requestMtu(operation.mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE))) {
                            Logger.w("[OperationTypeMtuChanged] fail 设置MTU失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val mtuChangedEvent = awaitFirstEvent<BluetoothGattOnMtuChangedEvent>(operation.address, 1000L)
                        if (mtuChangedEvent == null || mtuChangedEvent.status != BluetoothGatt.GATT_SUCCESS) {
                            Logger.w("[OperationTypeMtuChanged] fail 设置MTU失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        Logger.d("[OperationTypeMtuChanged] success 设置MTU成功")
                        operationResultFlow.tryEmit(operation.success(mtuChangedEvent.mtu))
                    }

                    is OperationTypeNotify -> {
                        // 开关通知
                        val gatt = gattMap[operation.address]
                        if (gatt == null) {
                            Logger.w("[OperationTypeNotify] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val characteristic = gatt.findCharacteristic(UUID.fromString(operation.characteristicUuid), UUID.fromString(operation.serviceUuid))
                        if (characteristic == null) {
                            Logger.w("[OperationTypeNotify] fail 未找到特征")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (!characteristic.isNotifiable()) {
                            Logger.w("[OperationTypeNotify] fail 特征不支持通知")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
                        if (descriptor == null) {
                            Logger.w("[OperationTypeNotify] fail 未找到描述符")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (!gatt.setCharacteristicNotification(characteristic, operation.enable)) {
                            Logger.w("[OperationTypeNotify] fail 设置通知失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val value = if (operation.enable)
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        descriptor.executeWrite(gatt, value)
                        val descriptorWriteEvent = awaitFirstEvent<BluetoothGattOnDescriptorWriteEvent>(operation.address, 1000L) {
                            it.descriptor.uuid == descriptor.uuid
                        }
                        if (descriptorWriteEvent == null || descriptorWriteEvent.status != BluetoothGatt.GATT_SUCCESS) {
                            Logger.w("[OperationTypeNotify] fail 设置通知失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        Logger.d("[OperationTypeNotify] success 设置通知成功")
                        operationResultFlow.tryEmit(operation.success())
                    }

                    is OperationTypeWrite -> {
                        // 写入数据
                        val gatt = gattMap[operation.address]
                        if (gatt == null) {
                            Logger.w("[OperationTypeWrite] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val characteristic = gatt.findCharacteristic(UUID.fromString(operation.characteristicUuid), UUID.fromString(operation.serviceUuid))
                        if (characteristic == null) {
                            Logger.w("[OperationTypeWrite] fail 未找到特征")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val writeType = when {
                            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            characteristic.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            else -> {
                                Logger.w("[OperationTypeWrite] fail 特征不支持写入")
                                operationResultFlow.tryEmit(operation.fail())
                                continue
                            }
                        }
                        characteristic.executeWrite(gatt, operation.value, writeType)
                        val characteristicWriteEvent = awaitFirstEvent<BluetoothGattOnCharacteristicWriteEvent>(operation.address, 2000L) {
                            it.characteristic.uuid == characteristic.uuid
                        }
                        if (characteristicWriteEvent == null || characteristicWriteEvent.status != BluetoothGatt.GATT_SUCCESS) {
                            Logger.w("[OperationTypeWrite] fail 写入特征失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        Logger.d("[OperationTypeWrite] success 写入特征成功")
                        operationResultFlow.tryEmit(operation.success())
                    }
                }
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
        if (isBleSupport().not()) throw Exception("not support ble")
        if (isBleEnable()) return
        // 蓝牙未开启，请求用户开启蓝牙
        ActivityManager.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun startScan() {
        scope.launch {
            // 如果Android API小于30，需要请求定位权限
            val isAndroidOver30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.R
            if (isAndroidOver30.not() && PermissionManager.request(locationPermissionArray)
                    .not()
            ) throw Exception("missing location permission")
            if (isAndroidOver30.not() && isLocationEnabled().not()) throw Exception("location off")
            if (PermissionManager.request(bluetoothPermissionArray)
                    .not()
            ) throw Exception("missing bluetooth permission")
            scanResults.clear()
            bluetoothScanner?.startScan(null, scanSettings, scanCallback)
        }
    }

    override fun stopScan() {
        bluetoothScanner?.stopScan(scanCallback)
    }

    override suspend fun connect(address: String): List<BluetoothGattService> {
        // 提交连接任务
        operationQueueChannel.send(OperationTypeConnect(address))
        val connectResult = operationResultFlow.awaitFirstOperationResult<OperationResultConnect>(address)
        if (connectResult.result.not()) throw Exception("connect failed")
        // 提交设置MTU任务
        operationQueueChannel.send(OperationTypeMtuChanged(address, GATT_MAX_MTU_SIZE))
        val mtuChangedResult = operationResultFlow.awaitFirstOperationResult<OperationResultMtuChanged>(address)
        Logger.i("set mtu to ${mtuChangedResult.mtu} ${if (mtuChangedResult.result) "success" else "fail"}")
        // 提交获取服务任务
        operationQueueChannel.send(OperationTypeDiscoverServices(address))
        val discoverServicesResult = operationResultFlow.awaitFirstOperationResult<OperationResultDiscoverServices>(address)
        if (discoverServicesResult.services.isEmpty()) {
            disconnect(address)
            throw Exception("discover services failed")
        }
        return discoverServicesResult.services
    }

    override fun disconnect(address: String) {
        gattMap[address]?.close()
        gattMap.remove(address)
        BluetoothController.onDeviceDisconnect(address)
    }

    override fun disconnectAll() {
        gattMap.keys.forEach { disconnect(it) }
    }

    override suspend fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        // 提交开关通知任务
        operationQueueChannel.send(OperationTypeNotify(address, serviceUuid, characteristicUuid, enable))
        val notifyResult = operationResultFlow.awaitFirstOperationResult<OperationResultNotify>(address)
        if (notifyResult.result.not()) throw Exception("notify failed")
    }

    override suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        // 提交写入任务
        operationQueueChannel.send(OperationTypeWrite(address, serviceUuid, characteristicUuid, value))
        val writeResult = operationResultFlow.awaitFirstOperationResult<OperationResultWrite>(address)
        if (writeResult.result.not()) throw Exception("write failed")
    }

    private suspend inline fun <reified T : BluetoothGattEvent> awaitFirstEvent(
        address: String,
        timeoutMillis: Long = 3000,
        crossinline predicate: (T) -> Boolean = { true }
    ): T? {
        return withTimeoutOrNull(timeoutMillis) {
            gattEventFlow.first {
                it is T && it.gatt.device.address.contentEquals(address, true) && predicate(it)
            } as T
        }
    }
}