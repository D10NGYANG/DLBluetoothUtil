package com.d10ng.bluetooth

/**
 * 注册 Web 端计划访问的 GATT 服务。
 *
 * 浏览器只允许访问设备选择器过滤项和 `optionalServices` 中声明的服务，因此必须在收集
 * [ABleManager.scan] 前调用。可重复注册，重复 UUID 会被去重。该函数只存在于 Web 源码集。
 *
 * @param uuid 标准服务名称、16/32 位 UUID 或完整 128 位服务 UUID。
 */
expect fun registerWebBleUseService(uuid: String)
