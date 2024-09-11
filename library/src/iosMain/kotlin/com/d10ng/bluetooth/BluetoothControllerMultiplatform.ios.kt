package com.d10ng.bluetooth

import com.d10ng.common.transform.toByteArray
import com.d10ng.common.transform.toNSData
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject

/**
 * 蓝牙控制器多平台实现
 * @Author d10ng
 * @Date 2024/9/10 15:35
 */
actual object BluetoothControllerMultiplatform {
    private val scope by lazy { CoroutineScope(Dispatchers.IO) }
    private val centralManager by lazy { CBCentralManager(delegate = centralDelegate, queue = null) }
    // 扫描设备
    private val scanDevices = mutableListOf<CBPeripheral>()
    // 已连接设备
    private val connectedDevices = mutableMapOf<CBPeripheral, Map<CBService, List<CBCharacteristic>>>()
    // 设备事件
    private val deviceEventFlow = MutableSharedFlow<CBCentralManagerEvent>()
    private val peripheralEventFlow = MutableSharedFlow<CBPeripheralEvent>()
    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            // 状态更新
            val state = CBManagerStateEnum.from(central.state)
            println("centralManagerDidUpdateState: ${state?.name}")
            if (state == CBManagerStateEnum.PoweredOn) startScan()
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber
        ) {
            println("didDiscoverPeripheral: ${didDiscoverPeripheral.name()} ${RSSI.intValue} ${didDiscoverPeripheral.identifier.UUIDString}")
            scanDevices.removeAll { it.identifier.UUIDString.contentEquals(didDiscoverPeripheral.identifier.UUIDString) }
            scanDevices.add(didDiscoverPeripheral)
            BluetoothController.onDeviceScan(BluetoothDevice(didDiscoverPeripheral.name(), didDiscoverPeripheral.identifier.UUIDString, RSSI.intValue))
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            println("didConnectPeripheral: ${didConnectPeripheral.name()} ${didConnectPeripheral.identifier.UUIDString}")
            val mtu = didConnectPeripheral.maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)
            println("mtu: $mtu")
            scope.launch {
                deviceEventFlow.emit(CBCentralManagerDidConnectEvent(didConnectPeripheral))
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?
        ) {
            println("didFailToConnectPeripheral: ${didFailToConnectPeripheral.name()} ${didFailToConnectPeripheral.identifier.UUIDString}")
            scope.launch {
                deviceEventFlow.emit(CBCentralManagerDidFailToConnectEvent(didFailToConnectPeripheral, error))
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            timestamp: CFAbsoluteTime,
            isReconnecting: Boolean,
            error: NSError?
        ) {
            println("didDisconnectPeripheral: ${didDisconnectPeripheral.name()} ${didDisconnectPeripheral.identifier.UUIDString}")
            val des = connectedDevices.filterKeys { it.identifier.UUIDString.contentEquals(didDisconnectPeripheral.identifier.UUIDString) }
            des.keys.forEach { connectedDevices.remove(it) }
            scope.launch {
                deviceEventFlow.emit(CBCentralManagerDidDisconnectEvent(didDisconnectPeripheral, timestamp, isReconnecting, error))
            }
            BluetoothController.onDeviceDisconnect(didDisconnectPeripheral.identifier.UUIDString)
        }
    }

    private val peripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheralDidUpdateName(peripheral: CBPeripheral) {
            println("peripheralDidUpdateName: ${peripheral.name()} ${peripheral.identifier.UUIDString}")
        }

        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            scope.launch {
                if (didDiscoverServices != null) {
                    println("Error with service discovery $didDiscoverServices")
                    peripheralEventFlow.emit(CBPeripheralDidDiscoverServicesEvent(null))
                    return@launch
                }
                val ls = peripheral.services?.mapNotNull { it as? CBService }
                if (ls.isNullOrEmpty()) {
                    peripheralEventFlow.emit(CBPeripheralDidDiscoverServicesEvent(emptyList()))
                    return@launch
                }
                peripheralEventFlow.emit(CBPeripheralDidDiscoverServicesEvent(ls))
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?
        ) {
            scope.launch {
                if (error != null) {
                    println("Error discovering characteristics: $error")
                    peripheralEventFlow.emit(CBPeripheralDidDiscoverCharacteristicsForServiceEvent(didDiscoverCharacteristicsForService, null))
                    return@launch
                }
                val ls = didDiscoverCharacteristicsForService.characteristics
                    ?.mapNotNull { it as? CBCharacteristic }
                if (ls.isNullOrEmpty()) {
                    peripheralEventFlow.emit(CBPeripheralDidDiscoverCharacteristicsForServiceEvent(didDiscoverCharacteristicsForService, emptyList()))
                    return@launch
                }
                peripheralEventFlow.emit(CBPeripheralDidDiscoverCharacteristicsForServiceEvent(didDiscoverCharacteristicsForService, ls))
            }
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            if (error != null) {
                println("Error update value for characteristics: $error")
            }
            val data = didUpdateValueForCharacteristic.value?.toByteArray()?: return
            scope.launch {
                val curKey = peripheral.identifier.UUIDString + " " + didUpdateValueForCharacteristic.service!!.UUID.UUIDString + " " + didUpdateValueForCharacteristic.UUID.UUIDString
                BluetoothController.notifyDataFlow.emit(curKey to data)
            }
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didWriteValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            if (error != null) {
                println("Error write value for characteristics: $error")
            }
            scope.launch {
                peripheralEventFlow.emit(CBPeripheralDidWriteValueForCharacteristicEvent(error == null))
            }
        }
    }

    /**
     * 是否支持蓝牙
     * @return Boolean
     */
    actual fun isBleSupport(): Boolean {
        return true
    }

    /**
     * 是否已开启蓝牙
     * @return Boolean
     */
    actual fun isBleEnable(): Boolean {
        return centralManager.state == CBManagerStatePoweredOn
    }

    /**
     * 开启蓝牙
     */
    actual suspend fun bleEnable() {
        // IOS没有对应的动作，开始扫描就会去申请开启蓝牙了
    }

    /**
     * 开始扫描
     */
    actual fun startScan() {
        stopScan()
        centralManager.scanForPeripheralsWithServices(null, null)
    }

    /**
     * 结束扫描
     */
    actual fun stopScan() {
        centralManager.stopScan()
    }

    /**
     * 连接设备
     * @param address String
     * @return List<BluetoothGattService>
     */
    actual suspend fun connect(address: String): List<BluetoothGattService> {
        val device = scanDevices.find { it.identifier.UUIDString.contentEquals(address) }?: throw Exception("device not found")
        centralManager.connectPeripheral(device, null)
        val event = deviceEventFlow.first()
        if (event is CBCentralManagerDidConnectEvent) {
            device.delegate = peripheralDelegate
            device.discoverServices(null)
            val servicesEvent = peripheralEventFlow.first { it is CBPeripheralDidDiscoverServicesEvent } as CBPeripheralDidDiscoverServicesEvent
            if (servicesEvent.services == null) {
                connectedDevices[device] = emptyMap()
                return emptyList()
            }
            val map = servicesEvent.services.map { service ->
                device.discoverCharacteristics(null, service)
                val characteristicsEvent = peripheralEventFlow.first { it is CBPeripheralDidDiscoverCharacteristicsForServiceEvent } as CBPeripheralDidDiscoverCharacteristicsForServiceEvent
                service to (characteristicsEvent.characteristics ?: listOf())
            }
            connectedDevices[device] = map.toMap()
            return map.map { (service, characteristics) ->
                BluetoothGattService(service.UUID.UUIDString, characteristics.map { BluetoothGattCharacteristic(it.UUID.UUIDString, it.properties.toInt()) })
            }
        }
        return emptyList()
    }

    /**
     * 断开连接
     */
    actual fun disconnect(address: String) {
        val device = connectedDevices.filterKeys { it.identifier.UUIDString.contentEquals(address) }.keys.firstOrNull()?: return
        centralManager.cancelPeripheralConnection(device)
        connectedDevices.remove(device)
    }

    /**
     * 断开连接
     */
    actual fun disconnectAll() {
        connectedDevices.forEach { centralManager.cancelPeripheralConnection(it.key) }
        connectedDevices.clear()
    }

    /**
     * 打开或关闭通知
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param enable Boolean
     */
    actual fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        val device = connectedDevices.filterKeys { it.identifier.UUIDString.contentEquals(address) }.keys.firstOrNull()
        if (device == null) throw Exception("device not found")
        val service = connectedDevices[device]!!.filterKeys { it.UUID.UUIDString.contentEquals(serviceUuid) }.keys.firstOrNull()
        if (service == null) throw Exception("service not found")
        val characteristic = connectedDevices[device]!![service]!!.firstOrNull { it.UUID.UUIDString.contentEquals(characteristicUuid) }
        if (characteristic == null) throw Exception("characteristic not found")
        if (characteristic.properties.toInt().bleGattCharacteristicNotifiable().not()) throw Exception("characteristic not support notify")
        if (characteristic.isNotifying != enable) {
            device.setNotifyValue(enable, characteristic)
        }
    }

    /**
     * 写入数据
     * @param address String
     * @param serviceUuid String
     * @param characteristicUuid String
     * @param value ByteArray
     */
    actual suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        val device = connectedDevices.filterKeys { it.identifier.UUIDString.contentEquals(address) }.keys.firstOrNull()
        if (device == null) throw Exception("device not found")
        val service = connectedDevices[device]!!.filterKeys { it.UUID.UUIDString.contentEquals(serviceUuid) }.keys.firstOrNull()
        if (service == null) throw Exception("service not found")
        val characteristic = connectedDevices[device]!![service]!!.firstOrNull { it.UUID.UUIDString.contentEquals(characteristicUuid) }
        if (characteristic == null) throw Exception("characteristic not found")
        if (characteristic.properties.toInt().bleGattCharacteristicWriteable().not()) throw Exception("characteristic not support write")
        device.writeValue(value.toNSData(), characteristic, CBCharacteristicWriteWithResponse)
        val event = peripheralEventFlow.first { it is CBPeripheralDidWriteValueForCharacteristicEvent } as CBPeripheralDidWriteValueForCharacteristicEvent
        if (event.result.not()) throw Exception("write error")
    }

}