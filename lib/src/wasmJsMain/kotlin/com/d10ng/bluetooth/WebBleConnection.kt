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
    private val disconnectHandler: (JsAny) -> Unit = { handleDisconnected() }

    init {
        runCatching {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val d = device.nativeHandle as BluetoothDevice
            d.addEventListener("gattserverdisconnected", disconnectHandler)
        }
    }

    override suspend fun discoverServices(): List<BleGattService> {
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
        return list
    }

    override suspend fun requestMaxMtu(): Int {
        // WEB 不支持MTU设置
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
        val promise = if (characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE)) {
            ch.writeValueWithoutResponse(uint8Array)
        } else {
            ch.writeValueWithResponse(uint8Array)
        }
        promise.await<JsAny>()
    }

    override suspend fun notify(
        characteristic: BleGattCharacteristic,
        enable: Boolean
    ) {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val ch = characteristic.nativeHandle as BluetoothRemoteGATTCharacteristic
        val characteristicKey = characteristic.serviceUuid.lowercase() to characteristic.uuid.lowercase()
        if (enable) {
            log.i { "Web: start notifications ${characteristic.uuid}" }
            ch.startNotifications().await<JsAny>()
            notifyHandlerMap.remove(characteristicKey)?.let { oldHandler ->
                notifyCharacteristicMap.remove(characteristicKey)?.removeEventListener(
                    "characteristicvaluechanged",
                    oldHandler
                )
            }
            val handler: (Event) -> Unit = { event ->
                val dataView = event.target.value
                val uint8Array = Uint8Array(dataView.buffer, dataView.byteOffset, dataView.byteLength)
                val byteArray = ByteArray(uint8Array.length)
                for (i in 0 until uint8Array.length) {
                    byteArray[i] = uint8Array[i]
                }
                mutableNotifyDataFlow.tryEmit(BleGattNotifyData(characteristic, byteArray))
            }
            notifyHandlerMap[characteristicKey] = handler
            notifyCharacteristicMap[characteristicKey] = ch
            ch.addEventListener("characteristicvaluechanged", handler)
        } else {
            log.i { "Web: stop notifications ${characteristic.uuid}" }
            val registeredCharacteristic = notifyCharacteristicMap[characteristicKey] ?: ch
            registeredCharacteristic.stopNotifications().await<JsAny>()
            notifyHandlerMap.remove(characteristicKey)?.let { h ->
                registeredCharacteristic.removeEventListener("characteristicvaluechanged", h)
            }
            notifyCharacteristicMap.remove(characteristicKey)
        }
        updateNotifyStatus(characteristic, enable)
    }

    override fun disconnect() {
        runCatching { gatt.disconnect() }
        handleDisconnected()
    }

    private fun handleDisconnected() {
        if (!mutableIsConnectedFlow.value) return
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
        }
    }
}
