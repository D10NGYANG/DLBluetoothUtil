package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨平台 BLE 入口，负责环境检测、设备发现和建立连接。
 *
 * 通过 [getPlatformBleManager] 获取当前平台的单例实现。扫描返回的 [BleDevice] 携带库内部使用的
 * 平台句柄，因此设备应交回同一平台的 [connect]，不应跨平台持久化整个对象；重连时持久化
 * [BleDevice.address] 并使用 [scanByAddress] 重新获取设备。
 *
 * 权限申请、系统开关交互和 Web 用户授权等平台差异由实现处理。各 Flow 的收集和取消语义见
 * 对应成员说明。
 */
abstract class ABleManager {

    /**
     * 判断当前运行环境是否提供本库所需的 BLE 能力。
     *
     * 返回 `true` 不代表权限已授予或蓝牙当前可用；实时可用状态由 [isEnabledFlow] 提供。
     */
    abstract fun isSupported(): Boolean

    /**
     * 蓝牙模块的可用状态。
     *
     * 这是只读热流，新订阅者会立即收到当前值。Android/iOS 表示系统蓝牙是否开启；Web 表示
     * 浏览器报告的 Web Bluetooth 可用性，不代表当前 Origin 已获得设备授权。
     */
    protected val mutableIsEnabledFlow = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = mutableIsEnabledFlow.asStateFlow()

    /**
     * 当前平台是否提供可由 [enable] 发起的蓝牙开启流程。
     *
     * 即使返回 `true`，最终结果仍可能取决于系统限制或用户选择，应继续观察 [isEnabledFlow]。
     */
    abstract fun isSupportEnable(): Boolean

    /**
     * 尝试发起平台允许的蓝牙开启流程。
     *
     * 该调用不保证返回时蓝牙已经开启。调用方应先检查 [isSupportEnable]，并以 [isEnabledFlow]
     * 作为最终状态来源。权限缺失、缺少可展示系统界面的宿主或平台能力不可用时可能抛出异常。
     */
    abstract suspend fun enable()

    /**
     * 创建一个设备发现 Flow。
     *
     * 收集 Flow 时开始发现，取消收集时释放扫描资源。[serviceUuids] 为空表示不按服务过滤。
     * Android/iOS 可持续产生扫描结果；Web 会打开系统设备选择器，通常只产生一个设备后结束，
     * 并且必须由用户手势触发收集。
     *
     * @param serviceUuids 用于过滤设备的 GATT 服务 UUID 列表。
     * @throws Throwable Android/iOS 的权限、蓝牙状态或平台扫描错误通过 Flow 结束异常传播；Web
     * 设备请求被拒绝或取消时，当前实现正常结束且不产生设备。
     */
    abstract fun scan(serviceUuids: List<String> = emptyList()): Flow<BleDevice>

    /**
     * 按平台设备标识查找已知设备，主要用于重连。
     *
     * Android 传入 MAC 地址（如 `AA:BB:CC:DD:EE:FF`）并执行过滤扫描；iOS 传入
     * `CBPeripheral.identifier` UUID；JS/WasmJS 传入 `BluetoothDevice.id`。iOS 和 Web 只会
     * 查询系统或当前 Origin 已知的设备，不会打开新的扫描或授权界面。
     *
     * @param addresses 非空的平台设备标识列表。
     */
    abstract fun scanByAddress(addresses: List<String>): Flow<BleDevice>

    /**
     * 连接由本管理器发现的 [device]。
     *
     * 成功返回前，连接对象已安装断开与通知监听器。连接失败、超时或 [device] 不属于当前平台时
     * 抛出异常。调用方不再使用连接时应调用 [ABleConnection.disconnect]。
     */
    abstract suspend fun connect(device: BleDevice): ABleConnection
}

/**
 * 获取当前目标平台的 [ABleManager] 单例。
 */
expect fun getPlatformBleManager(): ABleManager
