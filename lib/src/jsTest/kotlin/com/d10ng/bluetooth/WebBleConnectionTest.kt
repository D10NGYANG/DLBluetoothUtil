package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebBleConnectionTest {

    @Test
    fun notificationCompletingAfterDisconnectDoesNotRestoreListeners() = runTest {
        var resolve: (Unit) -> Unit = {}
        val started = Promise<Unit> { success, _ -> resolve = success }
        var listeners = 0
        val native: dynamic = js("({})")
        native.startNotifications = { started }
        native.addEventListener = { _: String, _: dynamic -> listeners++ }
        native.removeEventListener = { _: String, _: dynamic -> listeners-- }
        val characteristic = BleGattCharacteristic("ch", "service", setOf(BleGattCharacteristicProperty.NOTIFY), native)
        val gatt: dynamic = js("({})")
        gatt.disconnect = { }
        val connection = connection(gatt)
        val enabling = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { connection.notify(characteristic, true) }
        }
        connection.disconnect()
        resolve(Unit)
        assertTrue(enabling.await().isFailure)
        assertEquals(0, listeners)
        assertTrue(connection.notifyStatusFlow.value.isEmpty())
    }

    @Test
    fun oldConnectionCannotDisconnectReconnectedServer() {
        var disconnects = 0
        val gatt: dynamic = js("({})")
        gatt.disconnect = { disconnects++ }
        val old = connection(gatt)
        old.disconnect()
        val current = connection(gatt)
        old.disconnect()
        assertEquals(1, disconnects)
        assertTrue(current.isConnectedFlow.value)
        current.disconnect()
    }

    @Test
    fun discoverServicesAcceptsNativeJavascriptArrays() = runTest {
        val characteristic: dynamic = js("({})")
        characteristic.uuid = "characteristic"
        characteristic.properties = js(
            "({ broadcast: false, read: false, writeWithoutResponse: false, write: true, " +
                    "notify: false, indicate: false, authenticatedSignedWrites: false })"
        )

        val service: dynamic = js("({})")
        service.uuid = "service"
        service.getCharacteristics = { Promise.resolve(arrayOf(characteristic)) }

        val gatt: dynamic = js("({})")
        gatt.getPrimaryServices = { Promise.resolve(arrayOf(service)) }

        val services = connection(gatt).discoverServices()

        assertEquals(1, services.size)
        assertEquals("service", services.single().uuid)
        assertEquals("characteristic", services.single().characteristics.single().uuid)
    }

    @Test
    fun writePassesAUint8ArrayBufferSource() = runTest {
        var isBufferSource = false
        var written = byteArrayOf()
        val nativeCharacteristic: dynamic = js("({})")
        nativeCharacteristic.writeValueWithResponse = { payload: dynamic ->
            isBufferSource = js("ArrayBuffer.isView(payload)") as Boolean
            written = ByteArray(payload.length as Int) { index -> (payload[index] as Int).toByte() }
            Promise.resolve(Unit)
        }
        val characteristic = BleGattCharacteristic(
            uuid = "characteristic",
            serviceUuid = "service",
            properties = setOf(BleGattCharacteristicProperty.WRITE),
            nativeHandle = nativeCharacteristic
        )

        connection(js("({})")).write(characteristic, byteArrayOf(0x01, 0x7f, -0x01))

        assertTrue(isBufferSource)
        assertContentEquals(byteArrayOf(0x01, 0x7f, -0x01), written)
    }

    private fun connection(gatt: dynamic): WebBleConnection {
        val nativeDevice: dynamic = js("({})")
        nativeDevice.addEventListener = { _: String, _: dynamic -> Unit }
        nativeDevice.removeEventListener = { _: String, _: dynamic -> Unit }
        return WebBleConnection(
            device = BleDevice(
                name = "test",
                address = "device",
                rssi = 0,
                nativeHandle = nativeDevice
            ),
            gatt = gatt
        )
    }
}
