package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBPeripheralEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Peripheral events aggregator to avoid static initialization inside ObjC delegate.
 */
object BlePeripheralEvents {
    // Keep buffer small to avoid heavy static init
    val eventFlow = MutableSharedFlow<CBPeripheralEvent>(extraBufferCapacity = Int.MAX_VALUE)

    suspend inline fun <reified T : CBPeripheralEvent> first(
        address: String,
        crossinline predicate: (T) -> Boolean = { true }
    ): T = eventFlow.first {
        it is T && it.peripheral.address.contentEquals(address, true) && predicate(it)
    } as T
}