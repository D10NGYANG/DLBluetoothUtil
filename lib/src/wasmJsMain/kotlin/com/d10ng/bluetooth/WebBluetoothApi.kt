@file:OptIn(ExperimentalWasmJsInterop::class)

package com.d10ng.bluetooth

import kotlin.js.Promise

/**
 * Web bluetooth api 声明
 * @Author d10ng
 * @Date 2025/10/14 16:24
 */

external val navigator: Navigator

external interface Navigator : JsAny {
    val bluetooth: Bluetooth?
}

external interface Bluetooth : JsAny {
    fun getAvailability(): Promise<JsBoolean>
    fun getDevices(): Promise<JsArray<BluetoothDevice>>
    fun requestDevice(options: JsAny): Promise<BluetoothDevice>
}

fun createJsBluetoothRequestOptions(
    acceptAllDevices: Boolean,
    optionalServices: Array<String>,
) : JsAny = js("({ acceptAllDevices: acceptAllDevices, optionalServices: optionalServices })")

external interface BluetoothDevice : JsAny {
    val gatt: BluetoothRemoteGATTServer
    val id: String
    val name: String?
    fun addEventListener(type: String, listener: (event: JsAny) -> Unit)
    fun removeEventListener(type: String, listener: (event: JsAny) -> Unit)
}

external interface BluetoothRemoteGATTServer : JsAny {
    val connected: Boolean
    val device: BluetoothDevice
    fun connect(): Promise<BluetoothRemoteGATTServer>
    fun disconnect()
    fun getPrimaryService(uuid: String): Promise<BluetoothRemoteGATTService>
    fun getPrimaryServices(): Promise<JsArray<BluetoothRemoteGATTService>>
}

external interface BluetoothRemoteGATTService : JsAny {
    val isPrimary: Boolean
    val uuid: String
    val device: BluetoothDevice
    fun getCharacteristic(uuid: String): Promise<BluetoothRemoteGATTCharacteristic>
    fun getCharacteristics(): Promise<JsArray<BluetoothRemoteGATTCharacteristic>>
}

external interface BluetoothRemoteGATTCharacteristic : JsAny {
    val uuid: String
    val service: BluetoothRemoteGATTService
    val properties: BluetoothCharacteristicProperties
    fun startNotifications(): Promise<JsAny>
    fun stopNotifications(): Promise<JsAny>
    fun writeValueWithoutResponse(value: Uint8Array): Promise<JsAny>
    fun writeValueWithResponse(value: Uint8Array): Promise<JsAny>
    fun addEventListener(type: String, listener: (event: Event) -> Unit)
    fun removeEventListener(type: String, listener: (event: Event) -> Unit)
}

 external interface BluetoothCharacteristicProperties : JsAny {
    val broadcast: Boolean
    val read: Boolean
    val writeWithoutResponse: Boolean
    val write: Boolean
    val notify: Boolean
    val indicate: Boolean
    val authenticatedSignedWrites: Boolean
    val reliableWrite: Boolean
    val writableAuxiliaries: Boolean
}

external class Uint8Array: JsAny {
    constructor(length: Int)
    constructor(buffer: ArrayBuffer, byteOffset: Int = definedExternally, length: Int = definedExternally)
    val length: Int
    operator fun get(index: Int): Byte
    operator fun set(index: Int, value: Byte)
}

external class Event : JsAny {
    val target: EventTarget
}

external class EventTarget : JsAny {
    val value: DataView
}

external class DataView : JsAny {
    val buffer: ArrayBuffer
}

external class ArrayBuffer : JsAny {
    val byteLength: Int
}
