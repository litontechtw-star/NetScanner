package com.litontech.netscanner.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = CyanPrimary,
    onPrimary        = DeepSpace,
    primaryContainer = CardBg,
    onPrimaryContainer = CyanLight,
    secondary        = NeonGreen,
    onSecondary      = DeepSpace,
    secondaryContainer = CardBgAlt,
    onSecondaryContainer = NeonGreen,
    tertiary         = NeonPurple,
    background       = DeepSpace,
    onBackground     = TextPrimary,
    surface          = CardBg,
    onSurface        = TextPrimary,
    surfaceVariant   = DarkNavy,
    onSurfaceVariant = TextSecondary,
    outline          = BorderGlass,
    outlineVariant   = Divider,
    error            = SignalNone,
    onError          = Color.White,
)

@Composable
fun NetScannerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepSpace.toArgb()
            window.navigationBarColor = DarkNavy.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
