package com.d10ng.bluetooth

import com.d10ng.common.transform.toByteArray
import com.d10ng.common.transform.toNSData
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBDescriptor
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
object BluetoothControllerIOS: IBluetoothController {

    private val scope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    private const val GATT_MAX_MTU_SIZE = 517
    private const val GATT_MIN_MTU_SIZE = 23

    // 操作任务队列
    private val operationQueueChannel = Channel<OperationType>(capacity = Channel.UNLIMITED)
    // 操作结果
    private val operationResultFlow = MutableSharedFlow<OperationResult>(extraBufferCapacity = Int.MAX_VALUE)

    // 蓝牙状态
    private val stateFlow = MutableStateFlow(CBManagerStateEnum.Unknown)
    // 扫描设备
    private val scanDevices = mutableListOf<CBPeripheral>()
    // 已连接设备
    private val peripheralMap = mutableMapOf<String, CBPeripheral>()
    private val serviceMap = mutableMapOf<String, List<Pair<CBService, List<CBCharacteristic>>>>()
    // 设备事件
    private val deviceEventFlow = MutableSharedFlow<CBCentralManagerEvent>(extraBufferCapacity = Int.MAX_VALUE)
    private val peripheralEventFlow = MutableSharedFlow<CBPeripheralEvent>(extraBufferCapacity = Int.MAX_VALUE)

    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            // 状态更新
            val state = CBManagerStateEnum.from(central.state)
            stateFlow.value = state
            Logger.d("[CBCentralManagerDelegate.centralManagerDidUpdateState] state: ${state.name}")
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber
        ) {
            // 扫描结果
            val name = advertisementData["kCBAdvDataLocalName"]?.toString()?: didDiscoverPeripheral.name()
            Logger.d("[CBCentralManagerDelegate.didDiscoverPeripheral] address: ${didDiscoverPeripheral.address}, name: $name, RSSI: ${RSSI.intValue}")
            scanDevices.removeAll { it.address.contentEquals(didDiscoverPeripheral.address, true) }
            scanDevices.add(didDiscoverPeripheral)
            BluetoothController.onDeviceScan(BluetoothDevice(name, didDiscoverPeripheral.address, RSSI.intValue))
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            // 连接成功
            Logger.d("[CBCentralManagerDelegate.didConnectPeripheral] address: ${didConnectPeripheral.address}, name: ${didConnectPeripheral.name()}")
            deviceEventFlow.tryEmit(CBCentralManagerDidConnectEvent(didConnectPeripheral))
        }

        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?
        ) {
            // 连接失败
            Logger.w("[CBCentralManagerDelegate.didFailToConnectPeripheral] address: ${didFailToConnectPeripheral.address}, name: ${didFailToConnectPeripheral.name()}, error: $error")
            deviceEventFlow.tryEmit(CBCentralManagerDidFailToConnectEvent(didFailToConnectPeripheral, error))
        }

        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            timestamp: CFAbsoluteTime,
            isReconnecting: Boolean,
            error: NSError?
        ) {
            // 断开连接
            Logger.d("[CBCentralManagerDelegate.didDisconnectPeripheral] address: ${didDisconnectPeripheral.address}, name: ${didDisconnectPeripheral.name()}, timestamp: $timestamp, isReconnecting: $isReconnecting, error: $error")
            deviceEventFlow.tryEmit(CBCentralManagerDidDisconnectEvent(didDisconnectPeripheral, timestamp, isReconnecting, error))
            disconnect(didDisconnectPeripheral.address)
        }
    }

    private val centralManager = CBCentralManager(delegate = centralDelegate, queue = null)

    private val peripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheralDidUpdateName(peripheral: CBPeripheral) {
            Logger.d("[CBPeripheralDelegate.peripheralDidUpdateName] address: ${peripheral.address}, name: ${peripheral.name()}")
        }

        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            Logger.d("[CBPeripheralDelegate.didDiscoverServices] address: ${peripheral.address}, name: ${peripheral.name()}, error: $didDiscoverServices, services: ${peripheral.services}")
            if (didDiscoverServices != null) {
                peripheralEventFlow.tryEmit(CBPeripheralDidDiscoverServicesEvent(peripheral, null))
                return
            }
            val ls = peripheral.services?.mapNotNull { it as? CBService }
            peripheralEventFlow.tryEmit(CBPeripheralDidDiscoverServicesEvent(peripheral, ls))
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?
        ) {
            Logger.d("[CBPeripheralDelegate.didDiscoverCharacteristicsForService] address: ${peripheral.address}, name: ${peripheral.name()}, service: ${didDiscoverCharacteristicsForService.UUID.UUIDString}, error: $error, characteristics: ${didDiscoverCharacteristicsForService.characteristics}")
            if (error != null) {
                peripheralEventFlow.tryEmit(CBPeripheralDidDiscoverCharacteristicsForServiceEvent(peripheral, didDiscoverCharacteristicsForService, null))
                return
            }
            val ls = didDiscoverCharacteristicsForService.characteristics?.mapNotNull { it as? CBCharacteristic }
            peripheralEventFlow.tryEmit(CBPeripheralDidDiscoverCharacteristicsForServiceEvent(peripheral, didDiscoverCharacteristicsForService, ls))
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            val data = didUpdateValueForCharacteristic.value?.toByteArray()?: return
            Logger.d("[CBPeripheralDelegate.didUpdateValueForCharacteristic] address: ${peripheral.address}, name: ${peripheral.name()}, service: ${didUpdateValueForCharacteristic.serviceUuid}, characteristic: ${didUpdateValueForCharacteristic.characteristicUuid}, error: $error, data: ${data.toHexString(HexFormat.UpperCase)}")
            val curKey = peripheral.identifier.UUIDString + " " + didUpdateValueForCharacteristic.service!!.UUID.UUIDString + " " + didUpdateValueForCharacteristic.UUID.UUIDString
            BluetoothController.notifyDataFlow.tryEmit(curKey to data)
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didWriteValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            Logger.d("[CBPeripheralDelegate.didWriteValueForCharacteristic] address: ${peripheral.address}, name: ${peripheral.name()}, service: ${didWriteValueForCharacteristic.serviceUuid}, characteristic: ${didWriteValueForCharacteristic.characteristicUuid}, error: $error")
            peripheralEventFlow.tryEmit(CBPeripheralDidWriteValueForCharacteristicEvent(peripheral, error == null))
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didWriteValueForDescriptor: CBDescriptor,
            error: NSError?
        ) {
            Logger.d("[CBPeripheralDelegate.didWriteValueForDescriptor] address: ${peripheral.address}, name: ${peripheral.name()}, descriptor: ${didWriteValueForDescriptor.UUID.UUIDString}")
        }

        override fun peripheralIsReadyToSendWriteWithoutResponse(peripheral: CBPeripheral) {
            Logger.d("[CBPeripheralDelegate.isReadyToSendWriteWithoutResponse] address: ${peripheral.address}, name: ${peripheral.name()}")
            peripheralEventFlow.tryEmit(CBPeripheralIsReadyToSendWriteWithoutResponseEvent(peripheral))
        }
    }

    init {
        scope.launch {
            for (operation in operationQueueChannel) {
                when (operation) {
                    is OperationTypeConnect -> {
                        // 连接设备
                        val device = scanDevices.firstOrNull {
                            it.address.contentEquals(operation.address)
                        }
                        if (device == null) {
                            Logger.w("[OperationTypeConnect] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        centralManager.connectPeripheral(device, null)
                        val event = withTimeoutOrNull(5000) {
                            deviceEventFlow.first {
                                (it is CBCentralManagerDidConnectEvent || it is CBCentralManagerDidFailToConnectEvent)
                                        && it.peripheral.address.contentEquals(device.address, true)
                            }
                        }
                        if (event is CBCentralManagerDidConnectEvent) {
                            Logger.d("[OperationTypeConnect] success 连接成功")
                            peripheralMap[device.address] = device
                            operationResultFlow.tryEmit(operation.success())
                        } else {
                            Logger.d("[OperationTypeConnect] fail 连接失败")
                            operationResultFlow.tryEmit(operation.fail())
                        }
                    }

                    is OperationTypeDiscoverServices -> {
                        // 获取服务
                        val device = peripheralMap[operation.address]
                        if (device == null) {
                            Logger.w("[OperationTypeDiscoverServices] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        device.delegate = peripheralDelegate
                        device.discoverServices(null)
                        val event = withTimeoutOrNull(1000) {
                            peripheralEventFlow.first {
                                it is CBPeripheralDidDiscoverServicesEvent
                                        && it.peripheral.address.contentEquals(device.address, true)
                            } as CBPeripheralDidDiscoverServicesEvent
                        }
                        if (event == null || event.services.isNullOrEmpty()) {
                            Logger.d("[OperationTypeDiscoverServices] fail 获取服务失败")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val list = event.services.map { service ->
                            device.discoverCharacteristics(null, service)
                            val e = withTimeoutOrNull(1000) {
                                peripheralEventFlow.first {
                                    it is CBPeripheralDidDiscoverCharacteristicsForServiceEvent
                                            && it.peripheral.address.contentEquals(device.address, true)
                                            && it.service.serviceUuid.contentEquals(service.serviceUuid, true)
                                } as CBPeripheralDidDiscoverCharacteristicsForServiceEvent
                            }
                            service to (e?.characteristics ?: listOf())
                        }
                        serviceMap[operation.address] = list
                        val map = list.map { (service, characteristics) ->
                            BluetoothGattService(service.serviceUuid, characteristics.map {
                                BluetoothGattCharacteristic(it.characteristicUuid, it.properties.toInt())
                            })
                        }
                        operationResultFlow.tryEmit(operation.success(map))
                    }

                    is OperationTypeMtuChanged -> {
                        // 获取 MTU
                        val device = peripheralMap[operation.address]
                        if (device == null) {
                            Logger.w("[OperationTypeMtuChanged] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val mtu = device.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse)
                        operationResultFlow.tryEmit(operation.success(mtu.toInt()))
                    }

                    is OperationTypeNotify -> {
                        // 开关通知
                        val device = peripheralMap[operation.address]
                        if (device == null) {
                            Logger.w("[OperationTypeNotify] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val service = serviceMap[operation.address]?.firstOrNull {
                            it.first.serviceUuid.contentEquals(operation.serviceUuid, true)
                        }
                        if (service == null) {
                            Logger.w("[OperationTypeNotify] fail 未找到服务")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val characteristic = service.second.firstOrNull {
                            it.characteristicUuid.contentEquals(operation.characteristicUuid, true)
                        }
                        if (characteristic == null) {
                            Logger.w("[OperationTypeNotify] fail 未找到特征")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (!characteristic.properties.toInt().bleGattCharacteristicNotifiable()) {
                            Logger.w("[OperationTypeNotify] fail 特征不支持通知")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (characteristic.isNotifying != operation.enable) {
                            device.setNotifyValue(operation.enable, characteristic)
                        }
                        operationResultFlow.tryEmit(operation.success())
                    }

                    is OperationTypeWrite -> {
                        // 写入数据
                        val device = peripheralMap[operation.address]
                        if (device == null) {
                            Logger.w("[OperationTypeWrite] fail 未找到设备")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val service = serviceMap[operation.address]?.firstOrNull {
                            it.first.serviceUuid.contentEquals(operation.serviceUuid, true)
                        }
                        if (service == null) {
                            Logger.w("[OperationTypeWrite] fail 未找到服务")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        val characteristic = service.second.firstOrNull {
                            it.characteristicUuid.contentEquals(operation.characteristicUuid, true)
                        }
                        if (characteristic == null) {
                            Logger.w("[OperationTypeWrite] fail 未找到特征")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        if (!characteristic.properties.toInt().bleGattCharacteristicWriteable()) {
                            Logger.w("[OperationTypeWrite] fail 特征不支持写入")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        device.writeValue(operation.value.toNSData(), characteristic, CBCharacteristicWriteWithoutResponse)
                        val event = withTimeoutOrNull(1000) {
                            peripheralEventFlow.first {
                                it is CBPeripheralIsReadyToSendWriteWithoutResponseEvent
                                        && it.peripheral.address.contentEquals(device.address, true)
                            } as CBPeripheralIsReadyToSendWriteWithoutResponseEvent
                        }
                        if (event == null) {
                            Logger.w("[OperationTypeWrite] fail 写入超时")
                            operationResultFlow.tryEmit(operation.fail())
                            continue
                        }
                        operationResultFlow.tryEmit(operation.success())
                    }
                }
            }
        }
    }

    override fun isBleSupport(): Boolean {
        return true
    }

    override fun isBleEnable(): Boolean {
        return stateFlow.value == CBManagerStateEnum.PoweredOn
    }

    override suspend fun bleEnable() {
        // IOS没有对应的动作，开始扫描就会去申请开启蓝牙了
    }

    override fun startScan() {
        stopScan()
        scanDevices.clear()
        centralManager.scanForPeripheralsWithServices(null, null)
    }

    override fun stopScan() {
        centralManager.stopScan()
    }

    override suspend fun connect(address: String): List<BluetoothGattService> {
        // 提交连接任务
        operationQueueChannel.send(OperationTypeConnect(address))
        val connectResult = operationResultFlow.awaitFirstOperationResult<OperationResultConnect>(address)
        if (connectResult.result.not()) throw Exception("connect failed")
        // 提交获取服务任务
        operationQueueChannel.send(OperationTypeDiscoverServices(address))
        val discoverServicesResult = operationResultFlow.awaitFirstOperationResult<OperationResultDiscoverServices>(address)
        if (discoverServicesResult.services.isEmpty()) {
            disconnect(address)
            throw Exception("discover services failed")
        }
        return discoverServicesResult.services
    }

    override fun disconnect(address: String) {
        val device = peripheralMap[address] ?: return
        centralManager.cancelPeripheralConnection(device)
        peripheralMap.remove(address)
        serviceMap.remove(address)
        BluetoothController.onDeviceDisconnect(address)
    }

    override fun disconnectAll() {
        peripheralMap.keys.forEach { disconnect(it) }
    }

    override suspend fun requestMtu(address: String): Int {
        // 提交设置MTU任务
        operationQueueChannel.send(OperationTypeMtuChanged(address, GATT_MAX_MTU_SIZE))
        val mtuChangedResult = operationResultFlow.awaitFirstOperationResult<OperationResultMtuChanged>(address)
        Logger.i("set mtu to ${mtuChangedResult.mtu} ${if (mtuChangedResult.result) "success" else "fail"}")
        return if (mtuChangedResult.result) mtuChangedResult.mtu else GATT_MIN_MTU_SIZE
    }

    override suspend fun notify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean
    ) {
        // 提交开关通知任务
        operationQueueChannel.send(OperationTypeNotify(address, serviceUuid, characteristicUuid, enable))
        val notifyResult = operationResultFlow.awaitFirstOperationResult<OperationResultNotify>(address)
        if (notifyResult.result.not()) throw Exception("notify failed")
    }

    override suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ) {
        // 提交写入任务
        operationQueueChannel.send(OperationTypeWrite(address, serviceUuid, characteristicUuid, value))
        val writeResult = operationResultFlow.awaitFirstOperationResult<OperationResultWrite>(address)
        if (writeResult.result.not()) throw Exception("write failed")
    }
}