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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * ios蓝牙管理
 * @Author d10ng
 * @Date 2025/9/30 11:32
 */
object IosBleManager: ABleManager() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scanMutex = Mutex()

    init {
        IosOperationRunner.start()
        // 同步系统蓝牙状态到 isEnabledFlow
        scope.launch {
            BleCentralEvents.stateFlow.collect { state ->
                mutableIsEnabledFlow.value = state == CBManagerStateEnum.PoweredOn
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
        } else {
            log.d {
                "[bluetooth.enable_ignored] state=${BleCentralEvents.stateFlow.value.name}"
            }
        }
    }

    override fun scan(serviceUuids: List<String>): Flow<BleDevice> = callbackFlow {
        val startedAt = TimeSource.Monotonic.markNow()
        if (!scanMutex.tryLock()) {
            log.w {
                "[scan.rejected] type=service_filter serviceUuids=$serviceUuids reason=scan_already_active"
            }
            close(IllegalStateException("An iOS Bluetooth scan is already active"))
            return@callbackFlow
        }

        fun startScan() {
            val services = if (serviceUuids.isEmpty()) null
                           else serviceUuids.map { CBUUID.UUIDWithString(it) }
            IosOperationRunner.centralManager.scanForPeripheralsWithServices(services, null)
            log.i { "[scan.start] type=service_filter serviceUuids=$serviceUuids" }
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
                            nativeHandle = event.peripheral
                        )
                        val delivery = trySend(device)
                        if (delivery.isFailure && !delivery.isClosed) {
                            log.w {
                                "[scan.result_dropped] address=${device.address} " +
                                        "name=${device.name} error=${delivery.exceptionOrNull()}"
                            }
                        }
                    }
                }
            }
            if (BleCentralEvents.stateFlow.value != CBManagerStateEnum.PoweredOn) {
                log.w {
                    "[scan.rejected] type=service_filter serviceUuids=$serviceUuids " +
                            "reason=bluetooth_disabled state=${BleCentralEvents.stateFlow.value.name}"
                }
                close(Exception("Bluetooth disabled"))
            } else {
                // 监听蓝牙状态关闭
                launch {
                    isEnabledFlow.collect { isEnabled ->
                        if (!isEnabled) {
                            log.w {
                                "[scan.interrupted] type=service_filter " +
                                        "serviceUuids=$serviceUuids reason=bluetooth_disabled"
                            }
                            close(Exception("Bluetooth disabled"))
                        }
                    }
                }
                runCatching { withContext(Dispatchers.Main) { startScan() } }
                    .onFailure { error ->
                        log.e {
                            "[scan.start_failed] type=service_filter serviceUuids=$serviceUuids " +
                                    "error=${error.stackTraceToString()}"
                        }
                        close(error)
                    }
            }
        }

        awaitClose {
            log.i {
                "[scan.stop] type=service_filter serviceUuids=$serviceUuids " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            runCatching { IosOperationRunner.centralManager.stopScan() }.onFailure { error ->
                log.e {
                    "[scan.stop_failed] type=service_filter serviceUuids=$serviceUuids " +
                            "error=${error.stackTraceToString()}"
                }
            }
            eventsJob.cancel()
            scanMutex.unlock()
        }
    }

    override fun scanByAddress(addresses: List<String>): Flow<BleDevice> = callbackFlow {
        log.i { "[scan.start] type=known_addresses addresses=$addresses" }
        // iOS 不暴露真实 MAC，address 存的是 CBPeripheral.identifier.UUIDString
        // 通过 retrievePeripheralsWithIdentifiers 直接查找已知外设，无需启动扫描
        val peripherals = runCatching {
            val uuids = addresses.map { NSUUID(uUIDString = it) }
            @Suppress("UNCHECKED_CAST")
            IosOperationRunner.centralManager
                .retrievePeripheralsWithIdentifiers(uuids) as List<CBPeripheral>
        }.onFailure { error ->
            log.e {
                "[scan.failed] type=known_addresses addresses=$addresses " +
                        "error=${error.stackTraceToString()}"
            }
            close(error)
        }.getOrNull() ?: return@callbackFlow
        peripherals.forEach { peripheral ->
            val foundDevice = BleDevice(
                name = peripheral.name,
                address = peripheral.address,
                rssi = 0,
                nativeHandle = peripheral
            )
            log.d { "[scan.result] address=${foundDevice.address} name=${foundDevice.name}" }
            val delivery = trySend(foundDevice)
            if (delivery.isFailure && !delivery.isClosed) {
                log.w {
                    "[scan.result_dropped] address=${foundDevice.address} " +
                            "name=${foundDevice.name} error=${delivery.exceptionOrNull()}"
                }
            }
        }
        log.i {
            "[scan.stop] type=known_addresses addresses=$addresses results=${peripherals.size}"
        }
        close()
        awaitClose { }
    }

    override suspend fun connect(device: BleDevice): ABleConnection {
        val startedAt = TimeSource.Monotonic.markNow()
        log.i { "[connect.requested] address=${device.address} name=${device.name}" }
        val peripheral = device.nativeHandle as? CBPeripheral
            ?: run {
                log.e { "[connect.rejected] address=${device.address} name=${device.name} reason=invalid_native_handle" }
                throw IllegalArgumentException("BleDevice does not contain an iOS CBPeripheral")
            }
        val result = OperationManager.execute<OperationResult.Connect>(OperationType.Connect(device.address, peripheral))
        if (result == null || !result.result) throw Exception("Connect failed")
        val connection = IosBleConnection(device)
        return try {
            connection.awaitReady()
            log.i {
                "[connect.ready] address=${device.address} name=${device.name} " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            connection
        } catch (exception: Throwable) {
            log.e {
                "[connect.initialization_failed] address=${device.address} name=${device.name} " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds} error=${exception.stackTraceToString()}"
            }
            connection.disconnect()
            throw exception
        }
    }
}
