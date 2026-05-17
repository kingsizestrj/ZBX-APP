package com.zbxapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
fun ZbxTheme(
    forceDark: Boolean? = null,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val prefs by UiPreferences.state.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDark = forceDark ?: when (prefs.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wantDynamic = useDynamicColor && prefs.useDynamicColor && dynamicAvailable

    val colorScheme = when {
        wantDynamic && useDark -> dynamicDarkColorScheme(context)
        wantDynamic && !useDark -> dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insets = WindowCompat.getInsetsController(window, view)
                insets.isAppearanceLightStatusBars = !useDark
                insets.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
