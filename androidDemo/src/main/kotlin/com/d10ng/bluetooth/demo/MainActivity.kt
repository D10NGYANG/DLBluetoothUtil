package com.d10ng.bluetooth.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private var pendingPermissionRequest: CompletableDeferred<Boolean>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingPermissionRequest?.complete(hasBlePermissions())
        pendingPermissionRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            App(requestBlePermissions = ::requestBlePermissions)
            ImmersiveSystemBars()
        }
    }

    override fun onDestroy() {
        pendingPermissionRequest?.cancel()
        pendingPermissionRequest = null
        super.onDestroy()
    }

    private fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasBlePermissions(): Boolean = requiredBlePermissions().all { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun requestBlePermissions(): Boolean {
        if (hasBlePermissions()) return true
        pendingPermissionRequest?.let { return it.await() }

        val request = CompletableDeferred<Boolean>()
        pendingPermissionRequest = request
        permissionLauncher.launch(requiredBlePermissions())
        return try {
            request.await()
        } finally {
            if (pendingPermissionRequest === request) pendingPermissionRequest = null
        }
    }
}

@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val barColor = MaterialTheme.colorScheme.surface
    SideEffect {
        val activity = view.context as ComponentActivity
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = barColor.toArgb(),
                darkScrim = barColor.toArgb(),
                detectDarkMode = { darkTheme }
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = barColor.toArgb(),
                darkScrim = barColor.toArgb(),
                detectDarkMode = { darkTheme }
            )
        )
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
