package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.BleGattCharacteristic
import com.d10ng.bluetooth.constant.BleGattNotifyData
import com.d10ng.bluetooth.constant.BleGattService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 一个已建立的 BLE GATT 连接。
 *
 * 实例由 [ABleManager.connect] 创建并绑定到 [device]。服务发现、写入和通知订阅通过统一接口
 * 委托给平台实现；不再使用时必须调用 [disconnect] 释放原生连接和监听器。连接断开后该实例
 * 不可复用，应重新发现设备并建立新连接。库不会因为操作失败、超时或调用协程取消而主动断开
 * 已交付的连接；连接生命周期只由调用方显式 [disconnect]，或有平台断开回调等明确证据的系统/
 * 外设断开事件结束。确认连接已断开后，库会清理本地句柄、监听器、协程和公开状态。
 */
abstract class ABleConnection(
    /** 当前连接对应的设备。 */
    val device: BleDevice
) {

    companion object {
        /** Android GATT 允许请求的最大 ATT MTU。 */
        const val GATT_MAX_MTU_SIZE = 517

        /** BLE 默认 ATT MTU。 */
        const val GATT_MIN_MTU_SIZE = 23

        /** Client Characteristic Configuration Descriptor 的标准 UUID。 */
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"
        private const val NOTIFY_BUFFER_CAPACITY = 64
    }

    /**
     * 连接是否仍然有效的只读状态流。
     *
     * 新建连接以 `true` 开始；主动或被动断开后变为 `false`，并同步清空 [servicesFlow] 与
     * [notifyStatusFlow]。
     */
    protected val mutableIsConnectedFlow = MutableStateFlow(true)
    val isConnectedFlow: StateFlow<Boolean> = mutableIsConnectedFlow.asStateFlow()

    /** 最近一次 [discoverServices] 成功得到的服务列表。 */
    protected val mutableServicesFlow = MutableStateFlow<List<BleGattService>>(emptyList())
    val servicesFlow: StateFlow<List<BleGattService>> = mutableServicesFlow.asStateFlow()

    /** 当前由本库成功启用通知或指示的特征列表。 */
    protected val mutableNotifyStatusFlow = MutableStateFlow<List<BleGattCharacteristic>>(emptyList())
    val notifyStatusFlow: StateFlow<List<BleGattCharacteristic>> = mutableNotifyStatusFlow.asStateFlow()

    /** 按特征完整身份更新订阅状态，避免不同服务下相同特征 UUID 相互覆盖。 */
    protected fun updateNotifyStatus(characteristic: BleGattCharacteristic, enable: Boolean) {
        mutableNotifyStatusFlow.update { current ->
            current.filterNot { it == characteristic }.let { status ->
                if (enable) status + characteristic else status
            }
        }
    }

    /**
     * 外设主动发送的通知或指示数据。
     *
     * 该事件流不重放历史值，并额外缓冲 64 条。消费者落后时丢弃最旧事件，以避免原生回调线程
     * 被阻塞和内存无界增长。协议不能容忍丢包时，调用方必须增加序号、确认或重传机制。
     */
    protected val mutableNotifyDataFlow = MutableSharedFlow<BleGattNotifyData>(
        extraBufferCapacity = NOTIFY_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifyDataFlow: SharedFlow<BleGattNotifyData> = mutableNotifyDataFlow.asSharedFlow()

    /**
     * 发现远端 GATT 服务及其特征，并更新 [servicesFlow]。
     *
     * @return 当前连接发现的服务快照。
     * @throws Throwable 连接不可用、平台操作失败或移动端操作超时时抛出异常；失败不主动断开连接。
     */
    abstract suspend fun discoverServices(): List<BleGattService>

    /**
     * 获取当前平台可用于单次写入的最大 payload 长度。
     *
     * Android 将协商后的 ATT MTU 扣除 3 字节头；iOS 直接使用 CoreBluetooth 报告的最大写入
     * 长度；请求失败时回退为 20。Web 无法协商 MTU，固定返回 20。返回值用于调用方自行分包，
     * 而不是原始 ATT MTU。失败或超时不会主动断开连接。
     */
    abstract suspend fun requestMaxMtu(): Int

    /**
     * 向 [characteristic] 写入 [value]。
     *
     * 实现根据特征属性选择有响应写入或无响应写入。有响应写入等待平台确认；iOS 无响应写入会
     * 在系统发送队列繁忙时等待可写事件。调用方应按 [requestMaxMtu] 返回值自行分包。写入超时
     * 只结束本次调用，不主动断开连接；端到端送达、应答和重试必须由业务协议负责。
     *
     * @throws Throwable 特征不支持写入、连接不可用、平台拒绝或操作超时时抛出异常。
     */
    abstract suspend fun write(characteristic: BleGattCharacteristic, value: ByteArray)

    /**
     * 开启或关闭 [characteristic] 的通知/指示。
     *
     * 成功后更新 [notifyStatusFlow]，收到的数据从 [notifyDataFlow] 下发。特征必须声明 `NOTIFY`
     * 或 `INDICATE` 属性。
     *
     * @param enable `true` 开启，`false` 关闭。
     * @throws Throwable 特征不支持、平台配置失败或操作超时时抛出异常；失败不主动断开连接。
     */
    abstract suspend fun notify(characteristic: BleGattCharacteristic, enable: Boolean)

    /**
     * 主动断开并释放当前连接持有的原生资源。
     *
     * 该操作幂等且不等待平台断开回调；状态会立即反映到 [isConnectedFlow]。
     */
    abstract fun disconnect()

}
