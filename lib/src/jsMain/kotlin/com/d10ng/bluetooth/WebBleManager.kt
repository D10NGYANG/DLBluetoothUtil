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
import kotlin.js.Promise

/**
 * Web蓝牙管理
 * @Author d10ng
 * @Date 2025/10/11 15:06
 */
object WebBleManager: ABleManager() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val bluetooth: dynamic by lazy { js("navigator.bluetooth") }

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
                val available = (js("navigator.bluetooth.getAvailability()") as Promise<dynamic>).await()
                isEnabledFlow.value = available
            }
        }
    }

    override fun isSupported(): Boolean {
        return js("typeof navigator !== 'undefined' && 'bluetooth' in navigator") as Boolean
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
            val options = js("{}")
            options.acceptAllDevices = true
            options.optionalServices = optionalServices.toTypedArray()
            val device = runCatching {
                (bluetooth.requestDevice(options) as Promise<dynamic>).await()
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

    override suspend fun connect(device: BleDevice): ABleConnection {
        val gatt = (device.obj.asDynamic().gatt.connect() as Promise<dynamic>).await()
        return WebBleConnection(device, gatt)
    }
}