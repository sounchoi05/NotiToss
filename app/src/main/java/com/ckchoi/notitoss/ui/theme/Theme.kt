package com.ckchoi.notitoss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Mint40,
    onPrimary = Sand50,
    secondary = Coral60,
    background = Sand50,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Mint80,
    onPrimary = Slate20,
    secondary = Coral60,
    background = Slate20,
    surface = Slate30,
)

@Composable
fun NotifyTossTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
