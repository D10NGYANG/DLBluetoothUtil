package com.d10ng.bluetooth.demo

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            App()
            ImmersiveSystemBars()
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