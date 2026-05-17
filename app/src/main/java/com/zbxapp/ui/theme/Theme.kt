package com.zbxapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ZbxPrimary,
    onPrimary = ZbxOnPrimary,
    secondary = ZbxAccent,
    onSecondary = ZbxOnPrimary,
    background = ZbxDarkSurface,
    onBackground = ZbxOnSurface,
    surface = ZbxDarkSurface,
    onSurface = ZbxOnSurface,
    surfaceVariant = ZbxDarkSurfaceVariant,
    onSurfaceVariant = ZbxOnSurfaceMuted,
)

private val LightColors = lightColorScheme(
    primary = ZbxPrimary,
    secondary = ZbxAccent,
)

@Composable
fun ZbxTheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
