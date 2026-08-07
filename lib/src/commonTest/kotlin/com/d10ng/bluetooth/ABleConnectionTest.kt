package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import kotlin.test.Test
import kotlin.test.assertEquals

class ABleConnectionTest {

    @Test
    fun notifyStatusUsesServiceAndCharacteristicIdentity() {
        val connection = TestBleConnection()
        val first = characteristic(serviceUuid = "service-a")
        val second = characteristic(serviceUuid = "service-b")

        connection.recordNotifyStatus(first, true)
        connection.recordNotifyStatus(second, true)
        assertEquals(listOf(first, second), connection.notifyStatusFlow.value)

        connection.recordNotifyStatus(first, false)
        assertEquals(listOf(second), connection.notifyStatusFlow.value)
    }

    private fun characteristic(serviceUuid: String) = BleGattCharacteristic(
        uuid = "shared-characteristic",
        serviceUuid = serviceUuid,
        properties = setOf(BleGattCharacteristicProperty.NOTIFY),
        nativeHandle = Unit
    )

    private class TestBleConnection : ABleConnection(
        BleDevice(name = null, address = "test-device", rssi = 0, nativeHandle = Unit)
    ) {
        fun recordNotifyStatus(characteristic: BleGattCharacteristic, enable: Boolean) {
            updateNotifyStatus(characteristic, enable)
        }

        override suspend fun discoverServices(): List<BleGattService> = emptyList()

        override suspend fun requestMaxMtu(): Int = 20

        override suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray) = Unit

        override suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean) = Unit

        override fun disconnect() = Unit
    }
}
