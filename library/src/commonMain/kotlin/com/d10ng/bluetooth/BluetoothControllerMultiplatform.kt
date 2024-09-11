package com.d10ng.bluetooth

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
expect object BluetoothControllerMultiplatform {

    /**
     * 是否支持蓝牙
     * @return Boolean
     */
    fun isBleSupport(): Boolean

    /**
     * 是否已开启蓝牙
     * @return Boolean
     */
    fun isBleEnable(): Boolean

    /**
     * 开启蓝牙
     */
    suspend fun bleEnable()

    /**
     * 开始扫描
     */
    fun startScan()

    /**
     * 结束扫描
     */
    fun stopScan()

    /**
     * 连接设备
     * @param address String
     * @return List<BluetoothGattService>
     */
    suspend fun connect(address: String): List<BluetoothGattService>

    /**
     * 断开连接
     */
    fun disconnect(address: String)

    /**
     * 断开连接
     */
    fun disconnectAll()

    /**
     * 打开或关闭通知
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param enable Boolean
     */
    fun notify(address: String, serviceUuid: String, characteristicUuid: String, enable: Boolean)

    /**
     * 写入数据
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param value ByteArray
     */
    suspend fun write(address: String, serviceUuid: String, characteristicUuid: String, value: ByteArray)
}