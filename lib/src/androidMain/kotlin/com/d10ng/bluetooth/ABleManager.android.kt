package com.d10ng.bluetooth

actual fun getPlatformBleManager(): ABleManager {
    return AndroidBleManager
}