package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBPeripheralEvent
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBPeripheral

/**
 * ios操作执行器
 * @Author d10ng
 * @Date 2025/9/30 14:19
 */
object IosOperationRunner {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val centralManager = CBCentralManager(delegate = CBCentralManagerDelegate, queue = null)

    fun start() {
        log.d { "IosOperationRunner start" }
    }

    init {
        scope.launch {
            for (operation in OperationManager.queueChannel) {
                when (operation) {
                    is OperationType.Connect -> {
                        // 连接
                        launch { connect(operation) }
                    }
                    is OperationType.DiscoverServices -> {
                        // 服务发现
                        launch { discoverServices(operation) }
                    }
                    is OperationType.Notify -> {
                        // 开关通知
                        launch { notify(operation) }
                    }
                    is OperationType.Write -> {
                        // 写入
                        launch { write(operation) }
                    }
                    is OperationType.MtuChanged -> {
                        // 修改MTU
                        launch { requestMtu(operation) }
                    }
                }
            }
        }
    }

    private suspend fun connect(operation: OperationType.Connect) {
        val device = operation.obj as CBPeripheral
        centralManager.connectPeripheral(device, null)
        val event = CBCentralManagerDelegate.first<CBCentralManagerEvent.DidConnectResult>(operation.address)
        if (event.result) {
            log.d { "[OperationType.Connect] success 连接成功" }
            OperationManager.resultFlow.tryEmit(operation.success(event.peripheral))
        } else {
            log.d { "[OperationType.Connect] fail 连接失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
        }
    }

    private suspend fun discoverServices(operation: OperationType.DiscoverServices) {
        val device = operation.obj as CBPeripheral
        device.delegate = CBPeripheralDelegate
        device.discoverServices(null)
        val event = CBPeripheralDelegate.first<CBPeripheralEvent.DidDiscoverServices>(operation.address)
        if (event.services.isNullOrEmpty()) {
            log.d { "[OperationType.DiscoverServices] fail 获取服务失败" }
            OperationManager.resultFlow.tryEmit(operation.fail())
            return
        }

    }

    private suspend fun notify(operation: OperationType.Notify) {

    }

    private suspend fun write(operation: OperationType.Write) {

    }

    private suspend fun requestMtu(operation: OperationType.MtuChanged) {

    }
}