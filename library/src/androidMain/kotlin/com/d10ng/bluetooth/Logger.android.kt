package com.d10ng.bluetooth

import com.clj.fastble.BleManager

internal actual fun setThirdLibDebug(debug: Boolean) {
    BleManager.getInstance().enableLog(debug)
}