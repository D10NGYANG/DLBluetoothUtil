package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import kotlinx.coroutines.flow.Flow

/**
 * ios蓝牙管理
 * @Author d10ng
 * @Date 2025/9/30 11:32
 */
object IosBleManager: ABleManager() {

    init {
        IosOperationRunner.start()
    }

    override fun isSupported(): Boolean {
        return true
    }

    override fun isSupportEnable(): Boolean {
        return false
    }

    override suspend fun enable() {
        // IOS没有对应的动作，开始扫描就会去申请开启蓝牙了
    }

    override fun scan(): Flow<BleDevice> {
        TODO("Not yet implemented")
    }

    override suspend fun connect(device: BleDevice): ABleConnection {
        TODO("Not yet implemented")
    }
}