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
                runCatching {
                    navigator.bluetooth!!.getAvailability().await<JsBoolean>()
                }.onSuccess { available ->
                    mutableIsEnabledFlow.value = available.toBoolean()
                    log.i { "[bluetooth.availability] available=${available.toBoolean()}" }
                }.onFailure { error ->
                    log.e { "[bluetooth.availability_failed] error=${error.stackTraceToString()}" }
                }
            } else {
                log.w { "[bluetooth.unsupported]" }
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
            val options = if (serviceUuids.isEmpty()) {
                createJsBluetoothRequestOptions(true, optionalServices.map { it.toJsString() }.toJsArray())
            } else {
                val allOptional = (optionalServices + serviceUuids).map { it.toJsString() }.toJsArray()
                createJsBluetoothRequestOptionsWithFilters(
                    serviceUuids.map { it.toJsString() }.toJsArray(),
                    allOptional
                )
            }
            val device = runCatching {
                navigator.bluetooth!!.requestDevice(options).await<BluetoothDevice>()
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
                    "[scan.result] address=${foundDevice.address} name=${foundDevice.name}"
                }
                val delivery = trySend(foundDevice)
                if (delivery.isFailure && !delivery.isClosed) {
                    log.w {
                        "[scan.result_dropped] address=${foundDevice.address} " +
                                "name=${foundDevice.name} error=${delivery.exceptionOrNull()}"
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
                navigator.bluetooth!!.getDevices().await<JsArray<BluetoothDevice>>()
            }.onFailure { error ->
                log.e {
                    "[scan.failed] type=known_addresses addresses=$addresses " +
                            "error=${error.stackTraceToString()}"
                }
            }.getOrNull()
            var resultCount = 0
            if (allDevices != null) {
                for (i in 0 until allDevices.length) {
                    val device = allDevices[i] ?: continue
                    if (addresses.contains(device.id)) {
                        val foundDevice = BleDevice(
                            name = device.name ?: "Unknown",
                            address = device.id,
                            rssi = 0,
                            nativeHandle = device
                        )
                        resultCount++
                        log.d {
                            "[scan.result] address=${foundDevice.address} name=${foundDevice.name}"
                        }
                        val delivery = trySend(foundDevice)
                        if (delivery.isFailure && !delivery.isClosed) {
                            log.w {
                                "[scan.result_dropped] address=${foundDevice.address} " +
                                        "name=${foundDevice.name} error=${delivery.exceptionOrNull()}"
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

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override suspend fun connect(device: BleDevice): ABleConnection {
        log.i { "[connect.requested] address=${device.address} name=${device.name}" }
        return try {
            val d = device.nativeHandle as BluetoothDevice
            val gatt = d.gatt.connect().await<BluetoothRemoteGATTServer>()
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
