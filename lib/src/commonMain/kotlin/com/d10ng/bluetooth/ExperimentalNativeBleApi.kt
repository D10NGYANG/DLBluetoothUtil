package com.d10ng.bluetooth

/**
 * 标记绕过跨平台抽象、直接访问平台 BLE 对象的实验性接口。
 *
 * 该接口用于调用库尚未封装的原生能力。通过它发起 GATT 操作、修改 delegate/listener、断开或
 * 关闭连接，不会经过本库的按地址串行调度和状态同步，可能造成回调串线、操作超时或 Flow 状态
 * 失真。优先使用 [ABleManager] 与 [ABleConnection]；确需使用时，由调用方负责与库操作互斥。
 */
@RequiresOptIn(
    message = "Native BLE access can bypass DLBluetoothUtil state and operation management.",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalNativeBleApi
