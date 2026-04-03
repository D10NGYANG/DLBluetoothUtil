package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.BleDevice
import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBManagerStateEnum
import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import platform.CoreBluetooth.CBUUID
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSUUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ios蓝牙管理
 * @Author d10ng
 * @Date 2025/9/30 11:32
 */
object IosBleManager: ABleManager() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        return true
    }

    override suspend fun enable() {
        if (BleCentralEvents.stateFlow.value == CBManagerStateEnum.PoweredOff) {
            IosOperationRunner.restartCentralManager()
        }
    }

    override fun scan(serviceUuids: List<String>): Flow<BleDevice> = callbackFlow {

        fun startScan() {
            IosOperationRunner.centralManager.stopScan()
            val services = if (serviceUuids.isEmpty()) null
                           else serviceUuids.map { CBUUID.UUIDWithString(it) }
            IosOperationRunner.centralManager.scanForPeripheralsWithServices(services, null)
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
                close(Exception("Bluetooth disabled"))
            } else {
                // 监听蓝牙状态关闭
                launch {
                    isEnabledFlow.collect { isEnabled ->
                        if (!isEnabled) {
                            close(Exception("Bluetooth disabled"))
                        }
                    }
                }
                withContext(Dispatchers.Main) { startScan() }
            }
        }

        awaitClose {
            IosOperationRunner.centralManager.stopScan()
            eventsJob.cancel()
        }
    }

    override fun scanByAddress(addresses: List<String>): Flow<BleDevice> = callbackFlow {
        // iOS 不暴露真实 MAC，address 存的是 CBPeripheral.identifier.UUIDString
        // 通过 retrievePeripheralsWithIdentifiers 直接查找已知外设，无需启动扫描
        val uuids = addresses.map { NSUUID(uUIDString = it) }
        @Suppress("UNCHECKED_CAST")
        val peripherals = IosOperationRunner.centralManager
            .retrievePeripheralsWithIdentifiers(uuids) as List<CBPeripheral>
        peripherals.forEach { peripheral ->
            trySend(BleDevice(
                name = peripheral.name,
                address = peripheral.address,
                rssi = 0,
                obj = peripheral
            ))
        }
        close()
        awaitClose { }
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