package com.vs.gt.bledemo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DemoLightColors = lightColorScheme(
    primary = Color(0xFF006D77),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EBEF),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF5C6B2F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EBAA),
    onSecondaryContainer = Color(0xFF1A2000),
    surface = Color(0xFFFBFCFA),
    onSurface = Color(0xFF1A1C1C),
    surfaceVariant = Color(0xFFE5E9E7),
    onSurfaceVariant = Color(0xFF434846),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF1A1C1C),
)

private val DemoDarkColors = darkColorScheme(
    primary = Color(0xFF80D4DA),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFFB7EBEF),
    secondary = Color(0xFFC7D28F),
    onSecondary = Color(0xFF2E3606),
    secondaryContainer = Color(0xFF444D1B),
    onSecondaryContainer = Color(0xFFE2EBAA),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFC2C9C7),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E1),
)

/**
 * Demo 专用 Compose 主题。
 *
 * SDK 本身不依赖任何 UI 框架；这里使用 Compose 只是为了让示例代码更现代、更清晰。
 */
@Composable
fun GTBleDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DemoDarkColors else DemoLightColors,
        content = content,
    )
}
