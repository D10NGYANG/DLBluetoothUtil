package com.d10ng.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

/** Android BLE 前置环境检查与系统交互。 */
internal class AndroidBleEnvironment(
    private val context: Context,
) {

    fun hasPermissions(permissions: Array<String>): Boolean =
        permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun requestBluetoothEnable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** BluetoothDevice metadata is permission-gated on Android 12+; diagnostics must not break callbacks. */
@SuppressLint("MissingPermission")
internal fun BluetoothDevice.nameForLog(): String? = runCatching { name }
    .getOrElse { error -> "<unavailable:${error.message}>" }
