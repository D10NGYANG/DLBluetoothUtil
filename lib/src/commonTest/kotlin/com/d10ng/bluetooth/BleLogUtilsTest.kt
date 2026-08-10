package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattCharacteristicProperty
import com.d10ng.bluetooth.constant.BleGattService
import kotlin.test.Test
import kotlin.test.assertEquals

class BleLogUtilsTest {

    @Test
    fun byteArrayToHexLogUsesUppercaseTwoDigitBytes() {
        val value = byteArrayOf(0x00, 0x01, 0x0F, 0x10, 0x7F, 0x80.toByte(), 0xFF.toByte())

        assertEquals("00010F107F80FF", value.toHexLog())
    }

    @Test
    fun emptyByteArrayProducesEmptyHexPayload() {
        assertEquals("", byteArrayOf().toHexLog())
    }

    @Test
    fun communicationLogUsesCompactPositionalFieldsAndContinuousHex() {
        assertEquals(
            "[ble.tx] X1E-262711070201@12:7B:56:E2:37:56 " +
                    "00002760-08c2-11e1-9073-0e8ac72e1011/00002760-08c2-11e1-9073-0e8ac72e0012 " +
                    "7B 30302A34440D0A op=2 type=with-rsp",
            formatBleCommunicationLog(
                direction = "tx",
                address = "12:7B:56:E2:37:56",
                deviceName = "X1E-262711070201",
                serviceUuid = "00002760-08c2-11e1-9073-0e8ac72e1011",
                characteristicUuid = "00002760-08c2-11e1-9073-0e8ac72e0012",
                value = byteArrayOf(0x30, 0x30, 0x2A, 0x34, 0x44, 0x0D, 0x0A),
                details = "op=2 type=with-rsp"
            )
        )
    }

    @Test
    fun serviceDiscoveryLogIncludesCharacteristicsAndSortedProperties() {
        val characteristic = BleGattCharacteristic(
            uuid = "characteristic-uuid",
            serviceUuid = "service-uuid",
            properties = setOf(
                BleGattCharacteristicProperty.WRITE,
                BleGattCharacteristicProperty.NOTIFY
            ),
            nativeHandle = Unit
        )
        val services = listOf(
            BleGattService(
                uuid = "service-uuid",
                characteristics = listOf(characteristic),
                nativeHandle = Unit
            )
        )

        assertEquals(
            "[{serviceUuid=service-uuid, characteristics=" +
                    "[{uuid=characteristic-uuid, properties=[NOTIFY, WRITE]}]}]",
            services.toServiceDiscoveryLog()
        )
    }
}
