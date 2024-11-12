package com.d10ng.bluetooth

import com.bhm.ble.log.BleLogger
import com.clj.fastble.BleManager

internal actual fun setThirdLibDebug(debug: Boolean) {
    BleLogger.isLogger = debug
    BleManager.getInstance().enableLog(debug)
}