package com.d10ng.bluetooth.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import com.d10ng.bluetooth.getPlatformBleManager
import dlbluetoothutil_project.webdemo.generated.resources.MiSans_Normal
import dlbluetoothutil_project.webdemo.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    ComposeViewport {
        val font = preloadFont(Res.font.MiSans_Normal).value
        var fontsFallbackInitialized by remember { mutableStateOf(false) }
        val fontFamilyResolver = LocalFontFamilyResolver.current
        LaunchedEffect(fontFamilyResolver, font) {
            if (font != null) {
                // Preloads a fallback font with emojis to render missing glyphs that are not supported by the bundled font
                fontFamilyResolver.preload(FontFamily(listOf(font)))
                fontsFallbackInitialized = true
            }
        }
        val support = remember { getPlatformBleManager().isSupported() }
        if (!support) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f))) {
                Text("Bluetooth is not supported", modifier = Modifier.align(Alignment.Center))
            }
        } else if (font != null && fontsFallbackInitialized) {
            println("Fonts are ready")
            App()
        } else {
            // Displays the progress indicator to address a FOUT or the app being temporarily non-functional during loading
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f))) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            println("Fonts are not ready yet")
        }
    }
}