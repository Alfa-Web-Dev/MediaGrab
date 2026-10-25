package com.example.mediagrab.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.mediagrab.Settings

private val BrandLight = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    secondaryContainer = Color(0xFFCCE8E3),
    tertiary = Color(0xFF1565C0),
    background = Color(0xFFF6FAF8),
    surface = Color(0xFFF6FAF8),
    surfaceVariant = Color(0xFFDAE5E1),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFB0CCC7),
    secondaryContainer = Color(0xFF334B47),
    tertiary = Color(0xFF90CAF9),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF3F4946),
)

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF00796B), Color(0xFF1565C0)))

/** Returns true when the app is currently showing its dark theme. */
@Composable
fun isAppDark(): Boolean {
    val setting by Settings.theme.collectAsState()
    return when (setting) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun MediaGrabTheme(content: @Composable () -> Unit) {
    val dark = isAppDark()
    val dynamic by Settings.dynamicColors.collectAsState()
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        dynamic && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
