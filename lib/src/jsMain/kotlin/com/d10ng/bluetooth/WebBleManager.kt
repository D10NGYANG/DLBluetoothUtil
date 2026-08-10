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
                runCatching {
                    (js("navigator.bluetooth.getAvailability()") as Promise<dynamic>).await()
                }.onSuccess { available ->
                    mutableIsEnabledFlow.value = available
                    log.i { "[bluetooth.availability] available=$available" }
                }.onFailure { error ->
                    log.e { "[bluetooth.availability_failed] error=${error.stackTraceToString()}" }
                }
            } else {
                log.w { "[bluetooth.unsupported]" }
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

    override fun scan(serviceUuids: List<String>): Flow<BleDevice> = callbackFlow {
        if (!isSupported()) {
            log.w { "[scan.rejected] type=device_picker reason=unsupported" }
            close()
            return@callbackFlow
        }
        val job = launch {
            log.i {
                "[scan.start] type=device_picker serviceUuids=$serviceUuids optionalServices=$optionalServices"
            }
            val options = js("{}")
            if (serviceUuids.isEmpty()) {
                options.acceptAllDevices = true
                options.optionalServices = optionalServices.toTypedArray()
            } else {
                val filter = js("{}")
                filter.services = serviceUuids.toTypedArray()
                options.filters = arrayOf(filter)
                options.optionalServices = (optionalServices + serviceUuids).toTypedArray()
            }
            val device = runCatching {
                (bluetooth.requestDevice(options) as Promise<dynamic>).await()
            }.onFailure { error ->
                log.w {
                    "[scan.device_picker_closed] serviceUuids=$serviceUuids " +
                            "error=${error.stackTraceToString()}"
                }
            }.getOrNull()

            if (device != null) {
                val foundDevice = BleDevice(
                    name = device.name ?: "Unknown",
                    address = device.id,
                    rssi = 0,
                    nativeHandle = device
                )
                log.d {
                    "[scan.result] type=device_picker address=${foundDevice.address} name=${foundDevice.name}"
                }
                val delivery = trySend(foundDevice)
                if (delivery.isFailure && !delivery.isClosed) {
                    log.w {
                        "[scan.result_dropped] type=device_picker " +
                                "address=${foundDevice.address} name=${foundDevice.name} error=${delivery.exceptionOrNull()}"
                    }
                }
            }
            log.i { "[scan.stop] type=device_picker resultFound=${device != null}" }
            close()
        }
        awaitClose {
            if (job.isActive) log.d { "[scan.cancelled] type=device_picker" }
            job.cancel()
        }
    }

    override fun scanByAddress(addresses: List<String>): Flow<BleDevice> = callbackFlow {
        if (!isSupported()) {
            log.w { "[scan.rejected] type=known_addresses addresses=$addresses reason=unsupported" }
            close()
            return@callbackFlow
        }
        val job = launch {
            log.i { "[scan.start] type=known_addresses addresses=$addresses" }
            // 从此 Origin 下曾经授权过的设备中按 ID 过滤
            val allDevices = runCatching {
                (bluetooth.getDevices() as Promise<dynamic>).await()
            }.onFailure { error ->
                log.e {
                    "[scan.failed] type=known_addresses addresses=$addresses " +
                            "error=${error.stackTraceToString()}"
                }
            }.getOrNull()
            var resultCount = 0
            if (allDevices != null) {
                val len = allDevices.length as Int
                for (i in 0 until len) {
                    val device = allDevices[i]
                    if (addresses.contains(device.id as String)) {
                        val foundDevice = BleDevice(
                            name = device.name as? String ?: "Unknown",
                            address = device.id as String,
                            rssi = 0,
                            nativeHandle = device
                        )
                        resultCount++
                        log.d {
                            "[scan.result] type=known_addresses " +
                                    "address=${foundDevice.address} name=${foundDevice.name}"
                        }
                        val delivery = trySend(foundDevice)
                        if (delivery.isFailure && !delivery.isClosed) {
                            log.w {
                                "[scan.result_dropped] type=known_addresses " +
                                        "address=${foundDevice.address} name=${foundDevice.name} error=${delivery.exceptionOrNull()}"
                            }
                        }
                    }
                }
            }
            log.i { "[scan.stop] type=known_addresses addresses=$addresses results=$resultCount" }
            close()
        }
        awaitClose { job.cancel() }
    }

    override suspend fun connect(device: BleDevice): ABleConnection {
        log.i { "[connect.requested] address=${device.address} name=${device.name}" }
        return try {
            val gatt = (device.nativeHandle.asDynamic().gatt.connect() as Promise<dynamic>).await()
            log.i { "[connect.ready] address=${device.address} name=${device.name}" }
            WebBleConnection(device, gatt)
        } catch (error: Throwable) {
            log.e {
                "[connect.failed] address=${device.address} name=${device.name} " +
                        "error=${error.stackTraceToString()}"
            }
            throw error
        }
    }
}
