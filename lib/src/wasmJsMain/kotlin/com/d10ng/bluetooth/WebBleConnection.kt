@file:OptIn(ExperimentalWasmJsInterop::class)

package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import kotlinx.coroutines.await

/**
 * Web蓝牙连接
 * @Author d10ng
 * @Date 2025/10/14 17:02
 */
class WebBleConnection(
    device: BleDevice,
    private val gatt: BluetoothRemoteGATTServer
) : ABleConnection(device) {

    private val notifyHandlerMap = mutableMapOf<String, (Event) -> Unit>()
    private val notifyCharacteristicMap = mutableMapOf<String, BluetoothRemoteGATTCharacteristic>()
    private val disconnectHandler: (JsAny) -> Unit = { handleDisconnected() }

    init {
        runCatching {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val d = device.obj as BluetoothDevice
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
        val ch = characteristic.obj as BluetoothRemoteGATTCharacteristic
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
        val ch = characteristic.obj as BluetoothRemoteGATTCharacteristic
        val uuidKey = characteristic.uuid
        if (enable) {
            log.i { "Web: start notifications ${characteristic.uuid}" }
            ch.startNotifications().await<JsAny>()
            val handler: (Event) -> Unit = { event ->
                val dataView = event.target.value
                val uint8Array = Uint8Array(dataView.buffer)
                val byteArray = ByteArray(uint8Array.length)
                for (i in 0 until uint8Array.length) {
                    byteArray[i] = uint8Array[i]
                }
                notifyDataFlow.tryEmit(BleGattNotifyData(characteristic, byteArray))
            }
            notifyHandlerMap[uuidKey] = handler
            notifyCharacteristicMap[uuidKey] = ch
            ch.addEventListener("characteristicvaluechanged", handler)
        } else {
            log.i { "Web: stop notifications ${characteristic.uuid}" }
            ch.stopNotifications().await<JsAny>()
            notifyHandlerMap.remove(uuidKey)?.let { h ->
                ch.removeEventListener("characteristicvaluechanged", h)
            }
            notifyCharacteristicMap.remove(uuidKey)
        }
        val ls = notifyStatusFlow.value.filter { it.uuid != characteristic.uuid }.toMutableList()
        if (enable) ls += characteristic
        notifyStatusFlow.value = ls
    }

    override fun disconnect() {
        runCatching { gatt.disconnect() }
        handleDisconnected()
    }

    private fun handleDisconnected() {
        if (!isConnectedFlow.value) return
        isConnectedFlow.value = false
        servicesFlow.value = listOf()
        notifyStatusFlow.value = listOf()
        notifyHandlerMap.forEach { (uuid, handler) ->
            notifyCharacteristicMap[uuid]?.removeEventListener("characteristicvaluechanged", handler)
        }
        notifyHandlerMap.clear()
        notifyCharacteristicMap.clear()
        runCatching {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val d = device.obj as BluetoothDevice
            d.removeEventListener("gattserverdisconnected", disconnectHandler)
        }
    }
}
