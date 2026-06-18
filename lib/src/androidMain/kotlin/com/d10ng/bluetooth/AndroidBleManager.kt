package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.app.Activity
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
import com.d10ng.app.managers.ActivityManager
import com.d10ng.app.managers.PermissionManager
import com.d10ng.app.status.isLocationEnabled
import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * 蓝牙管理
 * @Author d10ng
 * @Date 2025/9/29 11:08
 */
object AndroidBleManager: ABleManager() {

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }

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
                                isEnabledFlow.value = true
                            }
                            BluetoothAdapter.STATE_OFF -> {
                                log.d { "Bluetooth is disabled" }
                                isEnabledFlow.value = false
                            }
                        }
                    }
                }
            }
        }, intentFilter)
        isEnabledFlow.value = bluetoothManager?.adapter?.isEnabled ?: false
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
            throw Exception("Device does not support Bluetooth")
        }
        if (bluetoothManager?.adapter?.isEnabled == true) {
            // 蓝牙已开启
            log.d { "Bluetooth is enabled" }
            return
        }
        val act = ActivityManager.top()
        if (act == null) {
            // 没有当前活动
            throw Exception("No activity to enable Bluetooth")
        }
        // 开启蓝牙
        val result = ActivityManager.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        if (result.resultCode == Activity.RESULT_OK) {
            // 用户同意开启
            log.i { "user agrees to enable Bluetooth" }
        } else {
            // 用户拒绝开启
            log.w { "User does not agree to enable Bluetooth" }
        }
    }

    @SuppressLint("MissingPermission")
    override fun scan(serviceUuids: List<String>): Flow<BleDevice> = callbackFlow {
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth scanner not available"))
            return@callbackFlow
        }

        // 如果Android API小于30，需要请求定位权限
        val isAndroidOver30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.R
        if (!isAndroidOver30 && !PermissionManager.request(locationPermissionArray)) {
            close(Exception("missing location permission"))
            return@callbackFlow
        }
        if (!isAndroidOver30 && !isLocationEnabled()) {
            close(Exception("location off"))
            return@callbackFlow
        }
        if (!PermissionManager.request(bluetoothPermissionArray)) {
            close(Exception("missing bluetooth permission"))
            return@callbackFlow
        }

        // 监听蓝牙状态关闭
        launch {
            isEnabledFlow.collect { isEnabled ->
                if (!isEnabled) {
                    close(Exception("Bluetooth disabled"))
                }
            }
        }

        val scanStartedAt = SystemClock.elapsedRealtime()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                log.d { "[ScanCallback.onScanResult] elapsedMs: ${SystemClock.elapsedRealtime() - scanStartedAt}, callbackType: $callbackType, result: $result" }
                trySend(BleDevice(result.device.name, result.device.address, result.rssi, result.device))
            }
            override fun onBatchScanResults(results: List<ScanResult?>?) {
                log.d { "[ScanCallback.onBatchScanResults] elapsedMs: ${SystemClock.elapsedRealtime() - scanStartedAt}, count: ${results?.size ?: 0}, results: $results" }
                results.orEmpty().filterNotNull().forEach { result ->
                    trySend(BleDevice(result.device.name, result.device.address, result.rssi, result.device))
                }
            }
            override fun onScanFailed(errorCode: Int) {
                log.w { "[ScanCallback.onScanFailed] Scan failed with error code $errorCode" }
                close(Exception("Scan failed with error code $errorCode"))
            }
        }

        log.d { "[scan.start] serviceUuids: $serviceUuids, mode: LOW_LATENCY" }
        scanner.startScan(
            if (serviceUuids.isEmpty()) null
            else serviceUuids.map { ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(it))).build() },
            scanSettings,
            callback
        )

        // 当 flow 被取消时停止扫描
        awaitClose {
            log.d { "[scan.stop] elapsedMs: ${SystemClock.elapsedRealtime() - scanStartedAt}" }
            runCatching { scanner.stopScan(callback) }
                .onFailure { e -> log.w { "stopScan failed: ${e.message}" } }
        }
    }

    @SuppressLint("MissingPermission")
    override fun scanByAddress(addresses: List<String>): Flow<BleDevice> = callbackFlow {
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth scanner not available"))
            return@callbackFlow
        }

        val isAndroidOver30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.R
        if (!isAndroidOver30 && !PermissionManager.request(locationPermissionArray)) {
            close(Exception("missing location permission"))
            return@callbackFlow
        }
        if (!isAndroidOver30 && !isLocationEnabled()) {
            close(Exception("location off"))
            return@callbackFlow
        }
        if (!PermissionManager.request(bluetoothPermissionArray)) {
            close(Exception("missing bluetooth permission"))
            return@callbackFlow
        }
        if (addresses.isEmpty()) {
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
                if (!isEnabled) close(Exception("Bluetooth disabled"))
            }
        }

        val scanStartedAt = SystemClock.elapsedRealtime()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                log.d { "[scanByAddress.onScanResult] elapsedMs: ${SystemClock.elapsedRealtime() - scanStartedAt}, callbackType: $callbackType, result: $result" }
                trySend(BleDevice(result.device.name, result.device.address, result.rssi, result.device))
            }
            override fun onBatchScanResults(results: List<ScanResult?>?) {
                log.d { "[scanByAddress.onBatchScanResults] elapsedMs: ${SystemClock.elapsedRealtime() - scanStartedAt}, count: ${results?.size ?: 0}, results: $results" }
                results.orEmpty().filterNotNull().forEach { result ->
                    trySend(BleDevice(result.device.name, result.device.address, result.rssi, result.device))
                }
            }
            override fun onScanFailed(errorCode: Int) {
                log.w { "[scanByAddress.onScanFailed] errorCode: $errorCode" }
                close(Exception("Scan failed with error code $errorCode"))
            }
        }

        log.d { "[scanByAddress.start] addresses: $addresses, mode: LOW_LATENCY" }
        scanner.startScan(
            addresses.map { ScanFilter.Builder().setDeviceAddress(it).build() },
            scanSettings,
            callback
        )

        awaitClose {
            log.d { "[scanByAddress.stop] elapsedMs: ${SystemClock.elapsedRealtime() - scanStartedAt}" }
            runCatching { scanner.stopScan(callback) }
                .onFailure { e -> log.w { "stopScan failed: ${e.message}" } }
        }
    }

    override suspend fun connect(device: BleDevice): ABleConnection {
        val result = OperationManager.execute<OperationResult.Connect>(OperationType.Connect(device.address, device.obj!!))
        if (result == null || !result.result) throw Exception("Connect failed")
        val connection = AndroidBleConnection(device, result.obj as BluetoothGatt)
        return try {
            connection.awaitReady()
            connection
        } catch (exception: Throwable) {
            connection.disconnect()
            throw exception
        }
    }
}
