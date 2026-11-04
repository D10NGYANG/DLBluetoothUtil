package com.d10ng.bluetooth

import com.d10ng.log.LogLevel
import com.d10ng.log.LoggerFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BleLoggerIntegrationTest {
    @Test
    fun communicationLogPreservesUnicodePercentAndHexThroughCollector() = runTest {
        val logger = BluetoothManagerLog
        val previousLevel = logger.miniLevel
        try {
            logger.miniLevel = LogLevel.NONE
            val entry = async(start = CoroutineStart.UNDISPATCHED) {
                LoggerFactory.logDataFlow.first { it.tag == "Bluetooth" }
            }
            logBleCommunication("rx", "test", { "设备😀 100% %@ %s" }, "service", "characteristic", byteArrayOf(0, -1))
            assertSame(logger, LoggerFactory.create("Bluetooth"))
            assertEquals("[ble.rx] 设备😀 100% %@ %s@test service/characteristic 2B 00FF", entry.await().message)
        } finally {
            logger.miniLevel = previousLevel
        }
    }
}
