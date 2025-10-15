package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import kotlinx.coroutines.await
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * Web蓝牙连接
 * @Author d10ng
 * @Date 2025/10/11 15:22
 */
class WebBleConnection(
    device: BleDevice,
    private val gatt: dynamic
) : ABleConnection(device) {

    private val notifyHandlerMap = mutableMapOf<String, (dynamic) -> Unit>()

    init {
        runCatching {
            device.obj.asDynamic().addEventListener("gattserverdisconnected") { _ ->
                handleDisconnected()
            }
        }
    }

    override suspend fun discoverServices(): List<BleGattService> {
        val services = (gatt.getPrimaryServices() as Promise<List<dynamic>>).await()
        val list = mutableListOf<BleGattService>()
        for (service in services) {
            val characteristics = (service.getCharacteristics() as Promise<List<dynamic>>).await()
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
        val ch = characteristic.obj.asDynamic()
        val promise = if (characteristic.properties.contains(BleGattCharacteristicProperty.WRITE_NO_RESPONSE)) {
            ch.writeValueWithoutResponse(value.toTypedArray()) as Promise<Unit>
        } else {
            ch.writeValueWithResponse(value.toTypedArray()) as Promise<Unit>
        }
        promise.await()
    }

    override suspend fun notify(
        characteristic: BleGattCharacteristic,
        enable: Boolean
    ) {
        val ch = characteristic.obj.asDynamic()
        val uuidKey = characteristic.uuid
        if (enable) {
            log.i { "Web: start notifications ${characteristic.uuid}" }
            (ch.startNotifications() as Promise<Unit>).await()
            val handler: (dynamic) -> Unit = { event ->
                val dataView = event.target.value as DataView
                val uint8Array = Uint8Array(dataView.buffer)
                val byteArray = ByteArray(uint8Array.length)
                for (i in 0 until uint8Array.length) {
                    byteArray[i] = uint8Array[i]
                }
                notifyDataFlow.tryEmit(BleGattNotifyData(characteristic, byteArray))
            }
            notifyHandlerMap[uuidKey] = handler
            ch.addEventListener("characteristicvaluechanged", handler)
        } else {
            log.i { "Web: stop notifications ${characteristic.uuid}" }
            (ch.stopNotifications() as Promise<Unit>).await()
            notifyHandlerMap.remove(uuidKey)?.let { h ->
                ch.removeEventListener("characteristicvaluechanged", h)
            }
        }
        val ls = notifyStatusFlow.value.filter { it.uuid != characteristic.uuid }.toMutableList()
        if (enable) ls += characteristic
        notifyStatusFlow.value = ls
    }

    override suspend fun disconnect() {
        runCatching { gatt.disconnect() }
        handleDisconnected()
    }

    private fun handleDisconnected() {
        isConnectedFlow.value = false
        servicesFlow.value = listOf()
        notifyStatusFlow.value = listOf()
    }
}