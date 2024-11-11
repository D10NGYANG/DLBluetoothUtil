package com.d10ng.bluetooth

import com.bhm.ble.log.BleLogger

internal actual fun setDebug(debug: Boolean) {
    BleLogger.isLogger = debug
}