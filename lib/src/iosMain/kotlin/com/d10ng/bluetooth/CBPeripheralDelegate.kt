package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.CBPeripheralEvent
import kotlinx.cinterop.ObjCSignatureOverride
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
internal val CBPeripheralDelegate: CBPeripheralDelegateProtocol = object : NSObject(), CBPeripheralDelegateProtocol {

    override fun peripheralDidUpdateName(peripheral: CBPeripheral) {
        log.d { "[CBPeripheralDelegate.peripheralDidUpdateName] address: ${peripheral.address}, name: ${peripheral.name()}" }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverServices: NSError?
    ) {
        // 服务发现
        val levelMessage = {
            "[gatt.services_discovered] address=${peripheral.address} " +
                    "name=${peripheral.name()} error=$didDiscoverServices services=${peripheral.services}"
        }
        if (didDiscoverServices == null) log.d(levelMessage) else log.w(levelMessage)
        if (didDiscoverServices != null) {
            BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverServices(peripheral, null))
            return
        }
        val ls = peripheral.services?.mapNotNull { it as? CBService }
        BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverServices(peripheral, ls))
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?
    ) {
        // 特征发现
        val levelMessage = {
            "[gatt.characteristics_discovered] address=${peripheral.address} " +
                    "name=${peripheral.name()} serviceUuid=${didDiscoverCharacteristicsForService.UUID.UUIDString} " +
                    "error=$error characteristics=${didDiscoverCharacteristicsForService.characteristics}"
        }
        if (error == null) log.d(levelMessage) else log.w(levelMessage)
        if (error != null) {
            BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverCharacteristicsForService(peripheral, didDiscoverCharacteristicsForService, null))
            return
        }
        val ls = didDiscoverCharacteristicsForService.characteristics?.mapNotNull { it as? CBCharacteristic }
        BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.DidDiscoverCharacteristicsForService(peripheral, didDiscoverCharacteristicsForService, ls))
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        // 订阅通知更新
        if (error != null) {
            log.w {
                "[ble.rx_failed] address=${peripheral.address} " +
                        "serviceUuid=${didUpdateValueForCharacteristic.serviceUUIDString} " +
                        "characteristicUuid=${didUpdateValueForCharacteristic.UUIDString} error=$error"
            }
            return
        }
        val data = didUpdateValueForCharacteristic.value?.toByteArray()
        if (data == null) {
            log.w {
                "[ble.rx_failed] address=${peripheral.address} " +
                        "serviceUuid=${didUpdateValueForCharacteristic.serviceUUIDString} " +
                        "characteristicUuid=${didUpdateValueForCharacteristic.UUIDString} error=value_is_null"
            }
            return
        }
        val ch = didUpdateValueForCharacteristic
        logBleCommunication(
            direction = "rx",
            address = peripheral.address,
            deviceName = { peripheral.name() },
            serviceUuid = ch.serviceUUIDString,
            characteristicUuid = ch.UUIDString,
            value = data
        )
        BlePeripheralEvents.notificationFlow.tryEmit(
            CBPeripheralEvent.DidUpdateValueForCharacteristic(peripheral, ch, data)
        )
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        val ch = didWriteValueForCharacteristic
        val levelMessage = {
            "[gatt.characteristic_write] address=${peripheral.address} " +
                    "name=${peripheral.name()} serviceUuid=${ch.serviceUUIDString} " +
                    "characteristicUuid=${ch.UUIDString} error=$error"
        }
        if (error == null) log.d(levelMessage) else log.w(levelMessage)
        BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.DidWriteValueForCharacteristic(peripheral, ch, error == null))
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        val ch = didUpdateNotificationStateForCharacteristic
        val levelMessage = {
            "[gatt.notification_state] address=${peripheral.address} " +
                    "serviceUuid=${ch.serviceUUIDString} characteristicUuid=${ch.UUIDString} " +
                    "isNotifying=${ch.isNotifying} error=$error"
        }
        if (error == null) log.d(levelMessage) else log.w(levelMessage)
        BlePeripheralEvents.eventFlow.tryEmit(
            CBPeripheralEvent.DidUpdateNotificationState(peripheral, ch, error == null)
        )
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForDescriptor: CBDescriptor,
        error: NSError?
    ) {
        log.d { "[CBPeripheralDelegate.didWriteValueForDescriptor] address: ${peripheral.address}, name: ${peripheral.name()}, descriptor: ${didWriteValueForDescriptor.UUID.UUIDString}" }
        BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.DidWriteValueForDescriptor(peripheral, didWriteValueForDescriptor, error == null))
    }

    override fun peripheralIsReadyToSendWriteWithoutResponse(peripheral: CBPeripheral) {
        log.d { "[CBPeripheralDelegate.isReadyToSendWriteWithoutResponse] address: ${peripheral.address}, name: ${peripheral.name()}" }
        BlePeripheralEvents.eventFlow.tryEmit(CBPeripheralEvent.IsReadyToSendWriteWithoutResponse(peripheral))
    }
}
