package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.os.SystemClock
import java.util.UUID
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * 蓝牙管理
 * @Author d10ng
 * @Date 2025/9/29 11:08
 */
object AndroidBleManager: ABleManager() {

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val environment by lazy { AndroidBleEnvironment(ctx) }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    init {
        // 启动任务执行器
        AndroidOperationRunner.start()
        // 注册蓝牙状态监听
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ctx.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val action = intent?.action ?: return
                when (action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        when (state) {
                            BluetoothAdapter.STATE_ON -> {
                                log.d { "Bluetooth is enabled" }
                                mutableIsEnabledFlow.value = true
                            }
                            BluetoothAdapter.STATE_OFF -> {
                                log.d { "Bluetooth is disabled" }
                                mutableIsEnabledFlow.value = false
                            }
                        }
                    }
                }
            }
        }, intentFilter)
        refreshBluetoothEnabledState()
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                environment.hasPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))

    @SuppressLint("MissingPermission")
    private fun refreshBluetoothEnabledState() {
        mutableIsEnabledFlow.value = if (hasBluetoothConnectPermission()) {
            runCatching { bluetoothManager?.adapter?.isEnabled == true }
                .onFailure { error -> log.w { "Unable to read Bluetooth state: ${error.message}" } }
                .getOrDefault(false)
        } else {
            false
        }
    }

    override fun isSupported(): Boolean {
        // 检查设备是否支持蓝牙
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return false
        }
        // 获取 BluetoothAdapter
        bluetoothManager?.adapter ?: return false
        // 检查设备是否支持蓝牙 BLE
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    override fun isSupportEnable(): Boolean {
        return true
    }

    override suspend fun enable() {
        if (!isSupported()) {
            // 设备不支持蓝牙
            log.w { "[bluetooth.enable_failed] reason=unsupported" }
            throw Exception("Device does not support Bluetooth")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !environment.hasPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
        ) {
            log.w { "[bluetooth.enable_failed] reason=missing_connect_permission" }
            throw SecurityException("missing bluetooth connect permission")
        }
        refreshBluetoothEnabledState()
        if (mutableIsEnabledFlow.value) {
            // 蓝牙已开启
            log.d { "Bluetooth is enabled" }
            return
        }
        runCatching { environment.requestBluetoothEnable() }
            .onSuccess { log.i { "[bluetooth.enable_requested]" } }
            .onFailure { error ->
                log.e { "[bluetooth.enable_failed] reason=system_dialog_unavailable error=${error.stackTraceToString()}" }
            }
            .getOrThrow()
    }

    @SuppressLint("MissingPermission")
    override fun scan(serviceUuids: List<String>): Flow<BleDevice> = callbackFlow {
        // Android 11 及以下扫描需要调用方先授予定位权限。
        val isAndroidOver30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.R
        if (!isAndroidOver30 && !environment.hasPermissions(locationPermissionArray)) {
            log.w { "[scan.rejected] type=service_filter reason=missing_location_permission" }
            close(Exception("missing location permission"))
            return@callbackFlow
        }
        if (!isAndroidOver30 && !environment.isLocationEnabled()) {
            log.w { "[scan.rejected] type=service_filter reason=location_off" }
            close(Exception("location off"))
            return@callbackFlow
        }
        if (!environment.hasPermissions(bluetoothPermissionArray)) {
            log.w { "[scan.rejected] type=service_filter reason=missing_bluetooth_permission" }
            close(Exception("missing bluetooth permission"))
            return@callbackFlow
        }

        refreshBluetoothEnabledState()
        if (!mutableIsEnabledFlow.value) {
            log.w { "[scan.rejected] type=service_filter reason=bluetooth_disabled" }
            close(Exception("Bluetooth disabled"))
            return@callbackFlow
        }
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            log.e { "[scan.rejected] type=service_filter reason=scanner_unavailable" }
            close(IllegalStateException("Bluetooth scanner not available"))
            return@callbackFlow
        }

        // 监听蓝牙状态关闭
        launch {
            isEnabledFlow.collect { isEnabled ->
                if (!isEnabled) {
                    log.w { "[scan.interrupted] type=service_filter reason=bluetooth_disabled" }
                    close(Exception("Bluetooth disabled"))
                }
            }
        }

        val scanStartedAt = SystemClock.elapsedRealtime()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val device = BleDevice(result.device.name, result.device.address, result.rssi, result.device)
                val delivery = trySend(device)
                if (delivery.isSuccess) {
                    log.d {
                        "[scan.result] address=${device.address} name=${device.name} rssi=${device.rssi}"
                    }
                } else if (!delivery.isClosed) {
                    log.w {
                        "[scan.result_dropped] address=${device.address} " +
                                "name=${device.name} reason=buffer_unavailable"
                    }
                }
            }
            override fun onBatchScanResults(results: List<ScanResult?>?) {
                results.orEmpty().filterNotNull().forEach { result ->
                    val device = BleDevice(result.device.name, result.device.address, result.rssi, result.device)
                    val delivery = trySend(device)
                    if (delivery.isSuccess) {
                        log.d {
                            "[scan.result] source=batch address=${device.address} " +
                                    "name=${device.name} rssi=${device.rssi}"
                        }
                    } else if (!delivery.isClosed) {
                        log.w {
                            "[scan.result_dropped] address=${device.address} " +
                                    "name=${device.name} reason=buffer_unavailable"
                        }
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                log.w { "[ScanCallback.onScanFailed] Scan failed with error code $errorCode" }
                close(Exception("Scan failed with error code $errorCode"))
            }
        }

        log.i { "[scan.start] type=service_filter serviceUuids=$serviceUuids mode=LOW_LATENCY" }
        runCatching {
            scanner.startScan(
                if (serviceUuids.isEmpty()) null
                else serviceUuids.map { ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(it))).build() },
                scanSettings,
                callback
            )
        }.onFailure { error ->
            log.e {
                "[scan.start_failed] type=service_filter serviceUuids=$serviceUuids " +
                        "error=${error.stackTraceToString()}"
            }
            close(error)
        }

        // 当 flow 被取消时停止扫描
        awaitClose {
            log.i { "[scan.stop] type=service_filter elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}" }
            runCatching { scanner.stopScan(callback) }
                .onFailure { e -> log.w { "stopScan failed: ${e.message}" } }
        }
    }

    @SuppressLint("MissingPermission")
    override fun scanByAddress(addresses: List<String>): Flow<BleDevice> = callbackFlow {
        val isAndroidOver30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.R
        if (!isAndroidOver30 && !environment.hasPermissions(locationPermissionArray)) {
            log.w { "[scan.rejected] type=address_filter addresses=$addresses reason=missing_location_permission" }
            close(Exception("missing location permission"))
            return@callbackFlow
        }
        if (!isAndroidOver30 && !environment.isLocationEnabled()) {
            log.w { "[scan.rejected] type=address_filter addresses=$addresses reason=location_off" }
            close(Exception("location off"))
            return@callbackFlow
        }
        if (!environment.hasPermissions(bluetoothPermissionArray)) {
            log.w { "[scan.rejected] type=address_filter addresses=$addresses reason=missing_bluetooth_permission" }
            close(Exception("missing bluetooth permission"))
            return@callbackFlow
        }
        refreshBluetoothEnabledState()
        if (!mutableIsEnabledFlow.value) {
            log.w { "[scan.rejected] type=address_filter addresses=$addresses reason=bluetooth_disabled" }
            close(Exception("Bluetooth disabled"))
            return@callbackFlow
        }
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            log.e { "[scan.rejected] type=address_filter addresses=$addresses reason=scanner_unavailable" }
            close(IllegalStateException("Bluetooth scanner not available"))
            return@callbackFlow
        }
        if (addresses.isEmpty()) {
            log.w { "[scan.rejected] type=address_filter reason=empty_addresses" }
            close(IllegalArgumentException("At least one Bluetooth address is required"))
            return@callbackFlow
        }

        val validAddressCount = addresses.count { address ->
            runCatching { bluetoothManager?.adapter?.getRemoteDevice(address) }
                .onFailure { log.w { "Invalid Bluetooth address: $address" } }
                .isSuccess
        }
        if (validAddressCount != addresses.size) {
            close(IllegalArgumentException("Invalid Bluetooth address"))
            return@callbackFlow
        }

        launch {
            isEnabledFlow.collect { isEnabled ->
                if (!isEnabled) {
                    log.w { "[scan.interrupted] type=address_filter addresses=$addresses reason=bluetooth_disabled" }
                    close(Exception("Bluetooth disabled"))
                }
            }
        }

        val scanStartedAt = SystemClock.elapsedRealtime()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val device = BleDevice(result.device.name, result.device.address, result.rssi, result.device)
                val delivery = trySend(device)
                if (delivery.isSuccess) {
                    log.d {
                        "[scan.result] address=${device.address} name=${device.name} rssi=${device.rssi}"
                    }
                } else if (!delivery.isClosed) {
                    log.w {
                        "[scan.result_dropped] address=${device.address} " +
                                "name=${device.name} reason=buffer_unavailable"
                    }
                }
            }
            override fun onBatchScanResults(results: List<ScanResult?>?) {
                results.orEmpty().filterNotNull().forEach { result ->
                    val device = BleDevice(result.device.name, result.device.address, result.rssi, result.device)
                    val delivery = trySend(device)
                    if (delivery.isSuccess) {
                        log.d {
                            "[scan.result] source=batch address=${device.address} " +
                                    "name=${device.name} rssi=${device.rssi}"
                        }
                    } else if (!delivery.isClosed) {
                        log.w {
                            "[scan.result_dropped] address=${device.address} " +
                                    "name=${device.name} reason=buffer_unavailable"
                        }
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                log.w { "[scanByAddress.onScanFailed] errorCode: $errorCode" }
                close(Exception("Scan failed with error code $errorCode"))
            }
        }

        log.i { "[scan.start] type=address_filter addresses=$addresses mode=LOW_LATENCY" }
        runCatching {
            scanner.startScan(
                addresses.map { ScanFilter.Builder().setDeviceAddress(it).build() },
                scanSettings,
                callback
            )
        }.onFailure { error ->
            log.e {
                "[scan.start_failed] type=address_filter addresses=$addresses " +
                        "error=${error.stackTraceToString()}"
            }
            close(error)
        }

        awaitClose {
            log.i { "[scan.stop] type=address_filter addresses=$addresses elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}" }
            runCatching { scanner.stopScan(callback) }
                .onFailure { e -> log.w { "stopScan failed: ${e.message}" } }
        }
    }

    override suspend fun connect(device: BleDevice): ABleConnection {
        val startedAt = TimeSource.Monotonic.markNow()
        log.i { "[connect.requested] address=${device.address} name=${device.name}" }
        val nativeDevice = device.nativeHandle as? android.bluetooth.BluetoothDevice
            ?: run {
                log.e { "[connect.rejected] address=${device.address} name=${device.name} reason=invalid_native_handle" }
                throw IllegalArgumentException("BleDevice does not contain an Android BluetoothDevice")
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !environment.hasPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
        ) {
            log.w { "[connect.rejected] address=${device.address} name=${device.name} reason=missing_connect_permission" }
            throw SecurityException("missing bluetooth connect permission")
        }
        val result = OperationManager.execute<OperationResult.Connect>(OperationType.Connect(device.address, nativeDevice))
        if (result == null || !result.result) throw Exception("Connect failed")
        val connectedGatt = result.obj as AndroidOperationRunner.ConnectedGatt
        val connection = AndroidBleConnection(device, connectedGatt.gatt, connectedGatt.callback)
        return try {
            connection.awaitReady()
            log.i {
                "[connect.ready] address=${device.address} name=${device.name} " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            connection
        } catch (exception: Throwable) {
            log.e {
                "[connect.initialization_failed] address=${device.address} name=${device.name} " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds} error=${exception.stackTraceToString()}"
            }
            connection.disconnect()
            throw exception
        }
    }
}
