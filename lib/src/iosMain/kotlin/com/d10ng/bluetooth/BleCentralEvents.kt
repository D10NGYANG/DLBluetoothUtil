package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBManagerStateEnum
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * 中心管理事件/状态流（与 ObjC 委托分离，避免静态初始化问题）
 */
object BleCentralEvents {
    // 蓝牙状态
    val stateFlow = MutableStateFlow(CBManagerStateEnum.Unknown)

    // 蓝牙事件（适度缓冲）
    val eventFlow = MutableSharedFlow<CBCentralManagerEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend inline fun <reified T : CBCentralManagerEvent> first(
        address: String,
        crossinline predicate: (T) -> Boolean = { true }
    ): T = eventFlow.first {
        it is T && it.peripheral.address.contentEquals(address, true) && predicate(it)
    } as T
}
