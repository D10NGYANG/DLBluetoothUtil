package com.d10ng.bluetooth

import com.d10ng.log.LoggerFactory

/**
 * 日志
 * @Author d10ng
 * @Date 2024/9/23 14:09
 */
internal val log by lazy { LoggerFactory.create("Bluetooth") }

// 提供给外部使用的日志名，避免冲突
val BluetoothManagerLog by lazy { log }