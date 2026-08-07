package com.d10ng.bluetooth.constant

/**
 * 扫描或已知设备查询得到的跨平台 BLE 设备快照。
 *
 * 实例只能由库创建，因为它同时保存当前平台的原生设备句柄。设备身份由 [address] 唯一确定，
 * [name] 与 [rssi] 不参与相等性比较；同一设备的新广播可以用 [withAdvertisement] 生成更新快照。
 *
 * @property name 广播或系统缓存中的设备名称，未知时为 `null`。
 * @property address 平台设备标识：Android 为 MAC，iOS 为 peripheral identifier，Web 为 device id。
 * @property rssi 最近一次扫描的信号强度；平台无法提供时可能为 `0`。
 */
class BleDevice internal constructor(
    val name: String?,
    val address: String,
    val rssi: Int,
    internal val nativeHandle: Any
) {
    /** 保留设备身份与原生句柄，仅更新广播元数据。 */
    fun withAdvertisement(
        name: String? = this.name,
        rssi: Int = this.rssi
    ): BleDevice = BleDevice(name, address, rssi, nativeHandle)

    override fun equals(other: Any?): Boolean =
        this === other || other is BleDevice && address == other.address

    override fun hashCode(): Int = address.hashCode()

    override fun toString(): String = "BleDevice(name=$name, address=$address, rssi=$rssi)"
}
