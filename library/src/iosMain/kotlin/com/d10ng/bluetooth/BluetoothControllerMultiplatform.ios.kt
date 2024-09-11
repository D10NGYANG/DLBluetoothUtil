package com.d10ng.bluetooth

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
actual object BluetoothControllerMultiplatform {
    /**
     * 是否支持蓝牙
     * @return Boolean
     */
    actual fun isBleSupport(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * 是否已开启蓝牙
     * @return Boolean
     */
    actual fun isBleEnable(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * 开启蓝牙
     */
    actual suspend fun bleEnable() {
    }

    /**
     * 开始扫描
     */
    actual fun startScan() {
    }

    /**
     * 结束扫描
     */
    actual fun stopScan() {
    }

    /**
     * 连接设备
     * @param address String
     * @return List<BluetoothGattService>
     */
    actual suspend fun connect(address: String): List<BluetoothGattService> {
        TODO("Not yet implemented")
    }

    /**
     * 断开连接
     */
    actual fun disconnect(address: String) {
    }

    /**
     * 断开连接
     */
    actual fun disconnectAll() {
    }

    /**
     * 打开或关闭通知
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param enable Boolean
     */
    actual fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
    }

    /**
     * 写入数据
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param value ByteArray
     */
    actual suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
    }

}