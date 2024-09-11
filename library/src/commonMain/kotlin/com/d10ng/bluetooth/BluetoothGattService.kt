package com.d10ng.bluetooth

/**
 * 蓝牙服务
 * @Author d10ng
 * @Date 2024/9/10 16:23
 */
data class BluetoothGattService(
    val uuid: String,
    val characteristics: List<BluetoothGattCharacteristic>
)

data class BluetoothGattCharacteristic(
    val uuid: String,
    val properties: Int
) {

    /**
     * 获取可用的属性
     * @return List<BluetoothGattCharacteristicProperty>
     */
    fun getProperties(): List<BluetoothGattCharacteristicProperty> {
        val list = mutableListOf<BluetoothGattCharacteristicProperty>()
        BluetoothGattCharacteristicProperty.entries.forEach { pro ->
            if (properties and pro.value != 0) {
                list.add(pro)
            }
        }
        return list
    }

    /**
     * 是否可读
     * @return Boolean
     */
    fun isReadable(): Boolean {
        return properties and BluetoothGattCharacteristicProperty.READ.value != 0
    }

    /**
     * 是否可写
     * @return Boolean
     */
    fun isWriteable(): Boolean {
        return properties and BluetoothGattCharacteristicProperty.WRITE.value != 0
                || properties and BluetoothGattCharacteristicProperty.WRITE_NO_RESPONSE.value != 0
    }

    /**
     * 是否可通知
     * @return Boolean
     */
    fun isNotifiable(): Boolean {
        return properties and BluetoothGattCharacteristicProperty.NOTIFY.value != 0
    }
}

enum class BluetoothGattCharacteristicProperty(val value: Int) {
    BROADCAST(1),
    READ(2),
    WRITE_NO_RESPONSE(4),
    WRITE(8),
    NOTIFY(16),
    INDICATE(32),
    SIGNED_WRITE(64),
    EXTENDED_PROPS(128)
}
