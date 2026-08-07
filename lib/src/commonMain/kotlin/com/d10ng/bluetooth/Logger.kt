package com.d10ng.bluetooth

import com.d10ng.log.LoggerFactory

/**
 * 日志
 * @Author d10ng
 * @Date 2024/9/23 14:09
 */
internal val log by lazy { LoggerFactory.create("Bluetooth") }

/** 本库使用的日志实例，可用于统一调整日志级别或接入日志收集。 */
val BluetoothManagerLog by lazy { log }
