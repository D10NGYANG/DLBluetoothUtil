package com.d10ng.bluetooth.constant

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 蓝牙服务
 * @Author d10ng
 * @Date 2025/9/28 17:53
 */
@OptIn(ExperimentalUuidApi::class)
data class BleGattService(
    val uuid: Uuid,
    val characteristics: List<BleGattCharacteristic>,
    // 平台对象
    val obj: Any? = null
)
