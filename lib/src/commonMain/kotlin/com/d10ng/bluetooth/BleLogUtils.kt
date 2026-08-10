package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import com.d10ng.bluetooth.constant.BleGattService

internal val OperationType.logName: String
    get() = when (this) {
        is OperationType.Connect -> "connect"
        is OperationType.DiscoverServices -> "discover_services"
        is OperationType.Notify -> "notify"
        is OperationType.Write -> "write"
        is OperationType.MtuChanged -> "request_mtu"
    }

internal fun OperationType.logFields(operationId: Long): String = buildString {
    append("operationId=$operationId operation=$logName address=$address")
    when (this@logFields) {
        is OperationType.Connect,
        is OperationType.DiscoverServices -> Unit
        is OperationType.Notify -> append(
            " serviceUuid=${characteristic.serviceUuid}" +
                    " characteristicUuid=${characteristic.uuid} enable=$enable"
        )
        is OperationType.Write -> append(
            " serviceUuid=${characteristic.serviceUuid}" +
                    " characteristicUuid=${characteristic.uuid} bytes=${value.size}"
        )
        is OperationType.MtuChanged -> append(" requestedMtu=$mtu")
    }
}

internal val OperationResult.succeeded: Boolean
    get() = when (this) {
        is OperationResult.Connect -> result
        is OperationResult.DiscoverServices -> services.isNotEmpty()
        is OperationResult.Notify -> result
        is OperationResult.Write -> result
        is OperationResult.MtuChanged -> result
    }

internal fun ByteArray.toHexLog(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(radix = 16).uppercase().padStart(2, '0')
}

internal fun List<BleGattService>.toServiceDiscoveryLog(): String = joinToString(
    prefix = "[",
    postfix = "]"
) { service ->
    buildString {
        append("{serviceUuid=${service.uuid}, characteristics=")
        append(service.characteristics.joinToString(prefix = "[", postfix = "]") { characteristic ->
            val properties = characteristic.properties
                .map { it.name }
                .sorted()
                .joinToString(prefix = "[", postfix = "]")
            "{uuid=${characteristic.uuid}, properties=$properties}"
        })
        append("}")
    }
}

internal fun logBleCommunication(
    direction: String,
    address: String,
    deviceName: () -> String?,
    serviceUuid: String,
    characteristicUuid: String,
    value: ByteArray,
    details: String = ""
) {
    log.d {
        val resolvedDeviceName = runCatching(deviceName)
            .getOrElse { error -> "<unavailable:${error.message}>" }
        formatBleCommunicationLog(
            direction = direction,
            address = address,
            deviceName = resolvedDeviceName,
            serviceUuid = serviceUuid,
            characteristicUuid = characteristicUuid,
            value = value,
            details = details
        )
    }
}

internal fun formatBleCommunicationLog(
    direction: String,
    address: String,
    deviceName: String?,
    serviceUuid: String,
    characteristicUuid: String,
    value: ByteArray,
    details: String = ""
): String = buildString {
    append("[ble.$direction] $deviceName@$address $serviceUuid/$characteristicUuid ${value.size}B")
    if (value.isNotEmpty()) append(" ${value.toHexLog()}")
    if (details.isNotEmpty()) append(" $details")
}
