package com.d10ng.bluetooth

import com.d10ng.log.LogLevel
import kotlin.test.Test

class AppleBleLoggerTest {
    @Test
    fun platformOutputAcceptsDeviceNamesContainingFormatTokens() {
        val logger = BluetoothManagerLog
        val previousLevel = logger.miniLevel
        try {
            logger.miniLevel = LogLevel.DEBUG
            logBleCommunication("rx", "test", { "设备😀 100% %@ %s" }, "service", "characteristic", byteArrayOf(0, -1))
        } finally {
            logger.miniLevel = previousLevel
        }
    }
}
