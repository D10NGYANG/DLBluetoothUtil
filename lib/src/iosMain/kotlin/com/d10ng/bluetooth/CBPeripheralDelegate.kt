package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBCentralManagerEvent
import com.d10ng.bluetooth.constant.CBPeripheralEvent
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * BLE 外设代理
 * @Author d10ng
 * @Date 2025/9/30 14:05
 */
object CBPeripheralDelegate : NSObject(), CBPeripheralDelegateProtocol {

    val eventFlow = MutableSharedFlow<CBPeripheralEvent>(extraBufferCapacity = Int.MAX_VALUE)

    override fun peripheralDidUpdateName(peripheral: CBPeripheral) {
        log.d { "[CBPeripheralDelegate.peripheralDidUpdateName] address: ${peripheral.address}, name: ${peripheral.name()}" }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverServices: NSError?
    ) {
        // 服务发现
        log.d { "[CBPeripheralDelegate.didDiscoverServices] address: ${peripheral.address}, name: ${peripheral.name()}, error: $didDiscoverServices, services: ${peripheral.services}" }
        if (didDiscoverServices != null) {
            eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverServices(peripheral, null))
            return
        }
        val ls = peripheral.services?.mapNotNull { it as? CBService }
        eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverServices(peripheral, ls))
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?
    ) {
        // 特征发现
        log.d { "[CBPeripheralDelegate.didDiscoverCharacteristicsForService] address: ${peripheral.address}, name: ${peripheral.name()}, service: ${didDiscoverCharacteristicsForService.UUID.UUIDString}, error: $error, characteristics: ${didDiscoverCharacteristicsForService.characteristics}" }
        if (error != null) {
            eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverCharacteristicsForService(peripheral, didDiscoverCharacteristicsForService, null))
            return
        }
        val ls = didDiscoverCharacteristicsForService.characteristics?.mapNotNull { it as? CBCharacteristic }
        eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverCharacteristicsForService(peripheral, didDiscoverCharacteristicsForService, ls))
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        // 订阅通知更新
        val data = didUpdateValueForCharacteristic.value?.toByteArray()?: return
        log.d { "[CBPeripheralDelegate.didUpdateValueForCharacteristic] address: ${peripheral.address}, name: ${peripheral.name()}, service: ${didUpdateValueForCharacteristic.serviceUuid}, characteristic: ${didUpdateValueForCharacteristic.characteristicUuid}, error: $error, data: ${data.toHexString(HexFormat.UpperCase)}" }
        eventFlow.tryEmit(CBPeripheralEvent.DidUpdateValueForCharacteristic(peripheral, didUpdateValueForCharacteristic, data))
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        log.d { "[CBPeripheralDelegate.didWriteValueForCharacteristic] address: ${peripheral.address}, name: ${peripheral.name()}, service: ${didWriteValueForCharacteristic.serviceUuid}, characteristic: ${didWriteValueForCharacteristic.characteristicUuid}, error: $error" }
        eventFlow.tryEmit(CBPeripheralEvent.DidWriteValueForCharacteristic(peripheral, didWriteValueForCharacteristic, error == null))
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForDescriptor: CBDescriptor,
        error: NSError?
    ) {
        log.d { "[CBPeripheralDelegate.didWriteValueForDescriptor] address: ${peripheral.address}, name: ${peripheral.name()}, descriptor: ${didWriteValueForDescriptor.UUID.UUIDString}" }
        eventFlow.tryEmit(CBPeripheralEvent.DidWriteValueForDescriptor(peripheral, didWriteValueForDescriptor, error == null))
    }

    override fun peripheralIsReadyToSendWriteWithoutResponse(peripheral: CBPeripheral) {
        log.d { "[CBPeripheralDelegate.isReadyToSendWriteWithoutResponse] address: ${peripheral.address}, name: ${peripheral.name()}" }
        eventFlow.tryEmit(CBPeripheralEvent.IsReadyToSendWriteWithoutResponse(peripheral))
    }

    suspend inline fun <reified T : CBPeripheralEvent> first(
        address: String,
        crossinline predicate: (T) -> Boolean = { true }
    ): T = eventFlow.first {
        it is T && it.peripheral.address.contentEquals(address, true) && predicate(it)
    } as T
}