package com.d10ng.bluetooth

import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported

/**
 * 蓝牙管理器状态
 * @Author d10ng
 * @Date 2024/9/11 15:57
 */
enum class CBManagerStateEnum(val value: CBManagerState) {
    Unknown(CBManagerStateUnknown),
    Resetting(CBManagerStateResetting),
    Unsupported(CBManagerStateUnsupported),
    Unauthorized(CBManagerStateUnauthorized),
    PoweredOff(CBManagerStatePoweredOff),
    PoweredOn(CBManagerStatePoweredOn);

    companion object {
        fun from(value: CBManagerState) = entries.firstOrNull { it.value == value }?: Unknown
    }
}