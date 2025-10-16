package com.d10ng.bluetooth

actual fun registerWebBleUseService(uuid: String) {
    WebBleManager.addService(uuid)
}