@file:OptIn(ExperimentalWasmJsInterop::class)

package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import kotlinx.coroutines.await

/**
 * Kotlin/WasmJS 的 Web Bluetooth 连接 Adapter。
 *
 * 浏览器 Promise 本身承担操作完成确认，因此不经过移动端的回调队列。每个通知特征只保留一个
 * DOM listener；重复启用会先移除旧 listener，断开时统一释放，避免重连后重复分发。
 */
internal class WebBleConnection(
    device: BleDevice,
    internal val gatt: BluetoothRemoteGATTServer
) : ABleConnection(device) {

    private val notifyHandlerMap = mutableMapOf<Pair<String, String>, (Event) -> Unit>()
    private val notifyCharacteristicMap = mutableMapOf<Pair<String, String>, BluetoothRemoteGATTCharacteristic>()
    private var disconnectRequestedByCaller = false
    private val disconnectHandler: (JsAny) -> Unit = {
        handleDisconnected(if (disconnectRequestedByCaller) "caller_request_callback" else "remote_event")
    }

    init {
        runCatching {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val d = device.nativeHandle as BluetoothDevice
            d.addEventListener("gattserverdisconnected", disconnectHandler)
        }.onSuccess {
            log.d { "[disconnect.listener_registered] address=${device.address}" }
        }.onFailure { error ->
            log.e {
                "[disconnect.listener_registration_failed] address=${device.address} " +
                        "error=${error.stackTraceToString()}"
            }
        }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        log.d { "[discover_services.start] address=${device.address}" }
        try {
            val services = gatt.getPrimaryServices().await<JsArray<BluetoothRemoteGATTService>>().toArray()
            val list = mutableListOf<BleGattService>()
            for (service in services) {
                val characteristics = service.getCharacteristics().await<JsArray<BluetoothRemoteGATTCharacteristic>>().toArray()
                list.add(BleGattService(
                service.uuid,
                characteristics.map { ch ->
                    val ps = mutableSetOf<BleGattCharacteristicProperty>()
                    if (ch.properties.broadcast) ps.add(BleGattCharacteristicProperty.BROADCAST)
                    if (ch.properties.read) ps.add(BleGattCharacteristicProperty.READ)
                    if (ch.properties.writeWithoutResponse) ps.add(BleGattCharacteristicProperty.WRITE_NO_RESPONSE)
                    if (ch.properties.write) ps.add(BleGattCharacteristicProperty.WRITE)
                    if (ch.properties.notify) ps.add(BleGattCharacteristicProperty.NOTIFY)
                    if (ch.properties.indicate) ps.add(BleGattCharacteristicProperty.INDICATE)
                    if (ch.properties.authenticatedSignedWrites) ps.add(BleGattCharacteristicProperty.SIGNED_WRITE)
                    BleGattCharacteristic(
                        ch.uuid,
                        service.uuid,
                        ps,
                        ch
                    )
                },
                service
                ))
            }
            mutableServicesFlow.value = list
            log.i {
                "[discover_services.success] address=${device.address} " +
                        "services=${list.size} characteristics=${list.sumOf { it.characteristics.size }} " +
                        "serviceDetails=${list.toServiceDiscoveryLog()}"
            }
            return list
        } catch (error: Throwable) {
            log.e {
                "[discover_services.failed] address=${device.address} error=${error.stackTraceToString()}"
            }
            throw error
        }
    }

    override suspend fun requestMaxMtu(): Int {
        // WEB 不支持MTU设置
        log.i { "[request_mtu.fallback] address=${device.address} reason=unsupported payloadLength=20" }
        return 20
    }

    override suspend fun write(
        characteristic: BleGattCharacteristic,
        value: ByteArray
    ) {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val ch = characteristic.nativeHandle as BluetoothRemoteGATTCharacteristic
        val uint8Array = Uint8Array(value.size)
        value.forEachIndexed { index, byte ->
            uint8Array[index] = byte
        }
        val writeType = if (characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE)) {
            "without_response"
        } else {
            "with_response"
        }
        logBleCommunication(
            direction = "tx",
            address = device.address,
            deviceName = { device.name },
            serviceUuid = characteristic.serviceUuid,
            characteristicUuid = characteristic.uuid,
            value = value,
            details = "type=${if (writeType == "without_response") "no-rsp" else "with-rsp"}"
        )
        try {
            val promise = if (writeType == "without_response") {
                ch.writeValueWithoutResponse(uint8Array)
            } else {
                ch.writeValueWithResponse(uint8Array)
            }
            promise.await<JsAny>()
            log.d {
                "[write.success] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                        "characteristicUuid=${characteristic.uuid} bytes=${value.size} writeType=$writeType"
            }
        } catch (error: Throwable) {
            log.e {
                "[write.failed] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                        "characteristicUuid=${characteristic.uuid} bytes=${value.size} writeType=$writeType " +
                        "error=${error.stackTraceToString()}"
            }
            throw error
        }
    }

    override suspend fun notify(
        characteristic: BleGattCharacteristic,
        enable: Boolean
    ) {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val ch = characteristic.nativeHandle as BluetoothRemoteGATTCharacteristic
        val characteristicKey = characteristic.serviceUuid.lowercase() to characteristic.uuid.lowercase()
        if (enable) {
            log.i {
                "[notify.start] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                        "characteristicUuid=${characteristic.uuid} enable=true"
            }
            try {
                ch.startNotifications().await<JsAny>()
            } catch (error: Throwable) {
                log.e {
                    "[notify.failed] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                            "characteristicUuid=${characteristic.uuid} enable=true error=${error.stackTraceToString()}"
                }
                throw error
            }
            notifyHandlerMap.remove(characteristicKey)?.let { oldHandler ->
                notifyCharacteristicMap.remove(characteristicKey)?.removeEventListener(
                    "characteristicvaluechanged",
                    oldHandler
                )
            }
            val handler: (Event) -> Unit = { event ->
                runCatching {
                    val dataView = event.target.value
                    val uint8Array = Uint8Array(dataView.buffer, dataView.byteOffset, dataView.byteLength)
                    val byteArray = ByteArray(uint8Array.length)
                    for (i in 0 until uint8Array.length) {
                        byteArray[i] = uint8Array[i]
                    }
                    logBleCommunication(
                        direction = "rx",
                        address = device.address,
                        deviceName = { device.name },
                        serviceUuid = characteristic.serviceUuid,
                        characteristicUuid = characteristic.uuid,
                        value = byteArray
                    )
                    mutableNotifyDataFlow.tryEmit(BleGattNotifyData(characteristic, byteArray))
                }.onFailure { error ->
                    log.e {
                        "[ble.rx_failed] address=${device.address} " +
                                "serviceUuid=${characteristic.serviceUuid} characteristicUuid=${characteristic.uuid} " +
                                "error=${error.stackTraceToString()}"
                    }
                }
            }
            notifyHandlerMap[characteristicKey] = handler
            notifyCharacteristicMap[characteristicKey] = ch
            ch.addEventListener("characteristicvaluechanged", handler)
        } else {
            log.i {
                "[notify.start] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                        "characteristicUuid=${characteristic.uuid} enable=false"
            }
            val registeredCharacteristic = notifyCharacteristicMap[characteristicKey] ?: ch
            try {
                registeredCharacteristic.stopNotifications().await<JsAny>()
            } catch (error: Throwable) {
                log.e {
                    "[notify.failed] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                            "characteristicUuid=${characteristic.uuid} enable=false error=${error.stackTraceToString()}"
                }
                throw error
            }
            notifyHandlerMap.remove(characteristicKey)?.let { h ->
                registeredCharacteristic.removeEventListener("characteristicvaluechanged", h)
            }
            notifyCharacteristicMap.remove(characteristicKey)
        }
        updateNotifyStatus(characteristic, enable)
        log.i {
            "[notify.success] address=${device.address} serviceUuid=${characteristic.serviceUuid} " +
                    "characteristicUuid=${characteristic.uuid} enable=$enable"
        }
    }

    override fun disconnect() {
        log.i { "[disconnect.requested] address=${device.address} source=caller_request" }
        disconnectRequestedByCaller = true
        runCatching { gatt.disconnect() }.onFailure { error ->
            log.e {
                "[disconnect.native_failed] address=${device.address} " +
                        "source=caller_request error=${error.stackTraceToString()}"
            }
        }
        handleDisconnected("caller_request")
    }

    private fun handleDisconnected(source: String) {
        if (!mutableIsConnectedFlow.value) {
            log.d { "[disconnect.ignored] address=${device.address} source=$source reason=already_disconnected" }
            return
        }
        log.i { "[disconnect.confirmed] address=${device.address} source=$source" }
        mutableIsConnectedFlow.value = false
        mutableServicesFlow.value = emptyList()
        mutableNotifyStatusFlow.value = emptyList()
        notifyHandlerMap.forEach { (characteristicKey, handler) ->
            notifyCharacteristicMap[characteristicKey]?.removeEventListener("characteristicvaluechanged", handler)
        }
        notifyHandlerMap.clear()
        notifyCharacteristicMap.clear()
        runCatching {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val d = device.nativeHandle as BluetoothDevice
            d.removeEventListener("gattserverdisconnected", disconnectHandler)
        }.onFailure { error ->
            log.e {
                "[disconnect.listener_removal_failed] address=${device.address} " +
                        "error=${error.stackTraceToString()}"
            }
        }
    }
}
