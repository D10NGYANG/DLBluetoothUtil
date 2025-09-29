package com.d10ng.bluetooth.constant

/**
 * 蓝牙 GATT 特征属性（Characteristic Properties）
 *
 * 这些属性定义了特征值支持的操作类型，对应 BLE 规范中的 Characteristic Properties 字段（1 字节，8 位）。
 * 每个属性对应一个 bit 位，多个属性可通过位或（OR）组合。
 *
 * 参考：
 * - Bluetooth Core Specification v5.3, Vol 3, Part G, Section 3.3.1.1
 * - Android: [BluetoothGattCharacteristic](https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic)
 * - iOS: CBCharacteristicProperties
 *
 * @property value 对应的十进制位值（用于位运算）
 *
 * @Author d10ng
 * @Date 2025/9/28 17:46
 */
enum class BluetoothGattCharacteristicProperty(val value: Int) {
    /**
     * 允许在“特征值广播”中包含此特征（通常用于 Advertising Data）。
     * 实际使用较少，多数设备不支持。
     */
    BROADCAST(1),

    /**
     * 允许客户端通过“读请求”获取该特征的当前值。
     */
    READ(2),

    /**
     * 允许客户端执行“无需响应的写入”（Write Without Response）。
     * 数据发送后不等待确认，速度快但不可靠。
     */
    WRITE_NO_RESPONSE(4),

    /**
     * 允许客户端执行“需要响应的写入”（Write With Response）。
     * 服务端收到后必须回复确认，可靠但速度较慢。
     */
    WRITE(8),

    /**
     * 允许服务端主动向客户端发送“通知”（Notification）。
     * 客户端需先启用 Client Characteristic Configuration Descriptor (CCCD)。
     * 通知不需客户端确认。
     */
    NOTIFY(16),

    /**
     * 允许服务端主动向客户端发送“指示”（Indication）。
     * 与通知类似，但客户端必须回复确认（类似 TCP ACK）。
     */
    INDICATE(32),

    /**
     * 允许“带签名的写入”（Signed Write / Authenticated Signed Writes）。
     * 用于未加密连接下的安全写入（通过数据签名防篡改）。
     * 需要配对并启用链路层签名。
     */
    SIGNED_WRITE(64),

    /**
     * 表示该特征具有“扩展属性”。
     * 客户端需读取“特征扩展属性描述符”（Characteristic Extended Properties Descriptor, UUID: 0x2900）
     * 以获取更多属性（如 Reliable Write、Auxiliary Write 等）。
     */
    EXTENDED_PROPS(128);

    companion object {
        /**
         * 从整数值解析出所有启用的属性
         * @param value 整数值
         * @return Set<BluetoothGattCharacteristicProperty>
         */
        fun fromValue(value: Int): Set<BluetoothGattCharacteristicProperty> {
            return entries.filter { (value and it.value) != 0 }.toSet()
        }

        /**
         * 将属性集合转换为位掩码值
         * @param properties 属性集合
         * @return Int
         */
        fun toValue(properties: Set<BluetoothGattCharacteristicProperty>): Int {
            return properties.sumOf { it.value }
        }
    }
}