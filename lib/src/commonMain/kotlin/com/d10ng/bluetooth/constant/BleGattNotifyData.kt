package com.d10ng.bluetooth.constant

/**
 * 通知数据
 * @Author d10ng
 * @Date 2025/9/29 10:41
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
