package com.d10ng.bluetooth.constant

/**
 * 服务发现得到的跨平台 GATT 特征快照。
 *
 * 实例只能由库创建，并绑定到发现它的原生特征对象。身份由忽略大小写的 [serviceUuid] 与 [uuid]
 * 共同确定，因此不同服务下相同 UUID 的特征不是同一对象。
 *
 * @property uuid 规范化前的平台特征 UUID 字符串。
 * @property serviceUuid 所属服务 UUID。
 * @property properties 平台报告的特征能力集合。
 */
class BleGattCharacteristic internal constructor(
    val uuid: String,
    val serviceUuid: String,
    val properties: Set<BleGattCharacteristicProperty>,
    internal val nativeHandle: Any
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is BleGattCharacteristic &&
                uuid.equals(other.uuid, ignoreCase = true) &&
                serviceUuid.equals(other.serviceUuid, ignoreCase = true)

    override fun hashCode(): Int = 31 * uuid.lowercase().hashCode() + serviceUuid.lowercase().hashCode()

    override fun toString(): String =
        "BleGattCharacteristic(uuid=$uuid, serviceUuid=$serviceUuid, properties=$properties)"
}
