package com.d10ng.bluetooth.constant

/**
 * 一次 GATT 通知或指示事件。
 *
 * [data] 使用内容相等性参与 [equals] 和 [hashCode]，但数组本身仍可变；收到后不应原地修改，
 * 需要长期保存时应复制。
 *
 * @property characteristic 产生事件的跨平台特征。
 * @property data 原生回调提供的 payload。
 */
data class BleGattNotifyData(
    val characteristic: BleGattCharacteristic,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BleGattNotifyData

        if (characteristic != other.characteristic) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characteristic.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
