package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Web蓝牙管理
 * @Author d10ng
 * @Date 2025/10/14 16:58
 */
@OptIn(ExperimentalWasmJsInterop::class)
object WebBleManager: ABleManager() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 想要访问的服务UUID列表
    private val optionalServices = mutableSetOf(
        "generic_access",
        "generic_attribute",
        "device_information",
        "battery_service"
    )

    init {
        scope.launch {
            if (isSupported()) {
                val available = navigator.bluetooth!!.getAvailability().await<Boolean>()
                isEnabledFlow.value = available
            }
        }
    }

    override fun isSupported(): Boolean {
        return navigator.bluetooth != null
    }

    override fun isSupportEnable(): Boolean {
        return false
    }

    override suspend fun enable() {
        // 不支持
    }

    /**
     * 增加需要访问的服务
     * @param uuid
     */
    fun addService(uuid: String) {
        optionalServices.add(uuid)
    }

    override fun scan(): Flow<BleDevice> = callbackFlow {
        if (!isSupported()) {
            close()
            return@callbackFlow
        }
        val job = launch {
            val options = createJsBluetoothRequestOptions(true, optionalServices.toTypedArray())
            val device = runCatching {
                navigator.bluetooth!!.requestDevice(options).await<BluetoothDevice>()
            }.getOrNull()

            if (device != null) {
                val ble = BleDevice(
                    name = device.name ?: "Unknown",
                    address = device.id,
                    rssi = 0,
                    obj = device
                )
                trySend(ble)
            }
            close()
        }
        awaitClose {
            job.cancel()
        }
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override suspend fun connect(device: BleDevice): ABleConnection {
        val d = device.obj as BluetoothDevice
        val gatt = d.gatt.connect().await<BluetoothRemoteGATTServer>()
        return WebBleConnection(device, gatt)
    }
}