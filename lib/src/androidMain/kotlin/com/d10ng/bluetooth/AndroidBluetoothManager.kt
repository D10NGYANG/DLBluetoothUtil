package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.d10ng.bluetooth.constant.BluetoothDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/**
 * 蓝牙管理
 * @Author d10ng
 * @Date 2025/9/29 11:08
 */
object AndroidBluetoothManager: ABluetoothManager() {

    private val bluetoothManager by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val bluetoothScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    init {
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
        isEnabledFlow.value = bluetoothAdapter?.isEnabled ?: false
    }

    override fun isSupported(): Boolean {
        // 检查设备是否支持蓝牙
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return false
        }
        // 获取 BluetoothAdapter
        bluetoothAdapter ?: return false
        // 检查设备是否支持蓝牙 BLE
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    override fun isSupportEnable(): Boolean {
        return true
    }

    override suspend fun enable() = suspendCancellableCoroutine { coroutine ->
        if (bluetoothAdapter?.isEnabled == true) {
            // 蓝牙已开启
            log.d { "Bluetooth is enabled" }
            coroutine.resume(Unit) { cause, _, _ -> null }
            return@suspendCancellableCoroutine
        }
        val act = CurrentActivityHolder.currentActivity
        if (act == null) {
            // 没有当前活动
            coroutine.resumeWithException(Exception("No activity to enable Bluetooth"))
            return@suspendCancellableCoroutine
        }
        // 开启蓝牙
        val launcher = act.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 用户同意开启
                log.i { "user agrees to enable Bluetooth" }
            } else {
                // 用户拒绝开启
                log.w { "User does not agree to enable Bluetooth" }
            }
            coroutine.resume(Unit) { cause, _, _ -> null }
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        launcher.launch(intent)
    }

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<BluetoothDevice> = callbackFlow {
        val scanner = bluetoothScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth scanner not available"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?: return
                log.d { "[ScanCallback.onScanResult] callbackType: $callbackType, result: $result" }
                val bleDevice = BluetoothDevice(result.device.name, result.device.address, result.rssi, result.device)
                trySend(bleDevice)
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("Scan failed with error code $errorCode"))
            }
        }

        scanner.startScan(null, scanSettings, callback)

        // 当 flow 被取消时停止扫描
        awaitClose {
            scanner.stopScan(callback)
        }
    }

    override suspend fun connect(device: BluetoothDevice): ABluetoothConnection {
        TODO("Not yet implemented")
    }
}