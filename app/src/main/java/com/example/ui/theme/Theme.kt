package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonIndigo,
    onPrimary = Color(0xFF111318),
    primaryContainer = Color(0xFF2F3036),
    onPrimaryContainer = NeonIndigo,
    secondary = NeonEmerald,
    onSecondary = Color(0xFF111318),
    secondaryContainer = NeonEmeraldDark,
    onSecondaryContainer = NeonEmerald,
    tertiary = NeonIndigoLight,
    background = CyberBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberCardBg,
    onSurfaceVariant = TextSecondary,
    outline = CyberCardBorder,
    error = NeonRose
)

private val LightColorScheme = DarkColorScheme // Default to high-contrast dark trading view theme

@Composable
fun CryptoBotTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    CryptoBotTheme(darkTheme, dynamicColor, content)
}

