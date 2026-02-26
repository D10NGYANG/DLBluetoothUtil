package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBManagerStateEnum
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ios蓝牙管理
 * @Author d10ng
 * @Date 2025/9/30 11:32
 */
object IosBleManager: ABleManager() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val centralManager = IosOperationRunner.centralManager

    init {
        IosOperationRunner.start()
        // 同步系统蓝牙状态到 isEnabledFlow
        scope.launch {
            BleCentralEvents.stateFlow.collect { state ->
                isEnabledFlow.value = state == CBManagerStateEnum.PoweredOn
            }
        }
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

    override fun scan(): Flow<BleDevice> = callbackFlow {

        fun startScan() {
            centralManager.stopScan()
            centralManager.scanForPeripheralsWithServices(null, null)
            log.d { "开始扫描" }
        }

        // 监听扫描事件并转发为通用设备模型
        val eventsJob = launch {
            launch {
                BleCentralEvents.eventFlow.collect { event ->
                    if (event is CBCentralManagerEvent.DidDiscoverPeripheral) {
                        val device = BleDevice(
                            name = event.name,
                            address = event.peripheral.address,
                            rssi = event.rssi,
                            obj = event.peripheral
                        )
                        trySend(device)
                    }
                }
            }
            if (BleCentralEvents.stateFlow.value != CBManagerStateEnum.PoweredOn) {
                BleCentralEvents.stateFlow.first { it == CBManagerStateEnum.PoweredOn }
                log.d { "蓝牙已开启" }
                withContext(Dispatchers.Main) { startScan() }
            }
        }

        withContext(Dispatchers.Main) { startScan() }

        awaitClose {
            centralManager.stopScan()
            eventsJob.cancel()
        }
    }

    override suspend fun connect(device: BleDevice): ABleConnection {
        val peripheral = device.obj ?: throw Exception("missing peripheral object")
        val result = OperationManager.execute<OperationResult.Connect>(OperationType.Connect(device.address, peripheral))
        if (result == null || !result.result) throw Exception("Connect failed")
        val connection = IosBleConnection(device)
        connection.awaitReady()
        return connection
    }
}