package com.d10ng.bluetooth.constant

/**
 * 服务发现得到的跨平台 GATT 服务快照。
 *
 * 实例只能由库创建。服务身份按忽略大小写的 [uuid] 比较；[characteristics] 和内部原生句柄
 * 不参与相等性比较。
 *
 * @property uuid 规范化前的平台服务 UUID 字符串。
 * @property characteristics 本次服务发现得到的特征快照。
 */
class BleGattService internal constructor(
    val uuid: String,
    val characteristics: List<BleGattCharacteristic>,
    internal val nativeHandle: Any
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is BleGattService && uuid.equals(other.uuid, ignoreCase = true)

    override fun hashCode(): Int = uuid.lowercase().hashCode()

    override fun toString(): String =
        "BleGattService(uuid=$uuid, characteristics=$characteristics)"
}
