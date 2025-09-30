package com.d10ng.bluetooth.constant

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 蓝牙特征值
 * @Author d10ng
 * @Date 2025/9/28 17:52
 */
@OptIn(ExperimentalUuidApi::class)
data class BleGattCharacteristic(
    val uuid: Uuid,
    val serviceUuid: Uuid,
    val properties: Set<BleGattCharacteristicProperty>,
    // 平台对象
    val obj: Any? = null
)
