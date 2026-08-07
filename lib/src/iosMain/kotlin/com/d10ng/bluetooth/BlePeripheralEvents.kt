package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBPeripheralEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

/**
 * CoreBluetooth peripheral delegate 事件聚合器。
 *
 * 独立对象避免在 Objective-C delegate 初始化期间创建复杂静态状态。控制事件进入 [eventFlow]，
 * 高频特征通知进入 [notificationFlow]，防止通知流量挤占操作确认事件。两个流都不重放历史值，
 * 缓冲满时丢弃最旧事件，因此 Runner 必须在发起原生操作前安装控制事件订阅。
 */
internal object BlePeripheralEvents {
    val eventFlow = MutableSharedFlow<CBPeripheralEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notificationFlow = MutableSharedFlow<CBPeripheralEvent.DidUpdateValueForCharacteristic>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend inline fun <reified T : CBPeripheralEvent> first(
        address: String,
        crossinline predicate: (T) -> Boolean = { true }
    ): T = eventFlow.first {
        it is T && it.peripheral.address.contentEquals(address, true) && predicate(it)
    } as T
}
